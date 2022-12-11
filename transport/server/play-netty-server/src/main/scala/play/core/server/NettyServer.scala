/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.core.server

import java.net.InetSocketAddress

import akka.Done
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import com.typesafe.config.ConfigMemorySize
import com.typesafe.config.ConfigValue
import com.typesafe.netty.HandlerPublisher
import com.typesafe.netty.http.HttpStreamsServerHandler
import io.netty.bootstrap.Bootstrap
import io.netty.channel._
import io.netty.channel.epoll.EpollChannelOption
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.unix.UnixChannelOption
import io.netty.handler.codec.http._
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslHandler
import io.netty.handler.timeout.IdleStateHandler
import play.api._
import play.api.http.HttpProtocol
import play.api.internal.libs.concurrent.CoordinatedShutdownSupport
import play.api.routing.Router
import play.core._
import play.core.server.Server.ServerStoppedReason
import play.core.server.netty._

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

sealed trait NettyTransport
case object Jdk    extends NettyTransport
case object Native extends NettyTransport

/**
 * creates a Server implementation based Netty
 */
class NettyServer(
    config: ServerConfig,
    val application: Application,
    stopHook: () => Future[_],
    val actorSystem: ActorSystem
)(implicit val materializer: Materializer)
    extends Server {
  initializeChannelOptionsStaticMembers()
  registerShutdownTasks()

  private val serverConfig         = config.configuration.get[Configuration]("play.server")
  private val nettyConfig          = serverConfig.get[Configuration]("netty")
  private val maxInitialLineLength = nettyConfig.get[Int]("maxInitialLineLength")
  private val maxHeaderSize =
    serverConfig.getDeprecated[ConfigMemorySize]("max-header-size", "netty.maxHeaderSize").toBytes.toInt
  private val maxContentLength = Server.getPossiblyInfiniteBytes(serverConfig.underlying, "max-content-length")
  private val maxChunkSize     = nettyConfig.get[Int]("maxChunkSize")
  private val threadCount      = nettyConfig.get[Int]("eventLoopThreads")
  private val logWire          = nettyConfig.get[Boolean]("log.wire")
  private val bootstrapOption  = nettyConfig.get[Config]("option")
  private val channelOption    = nettyConfig.get[Config]("option.child")
  private val httpIdleTimeout  = serverConfig.get[Duration]("http.idleTimeout")

  private lazy val transport = nettyConfig.get[String]("transport") match {
    case "native" => Native
    case "jdk"    => Jdk
    case _        => throw ServerStartException("Netty transport configuration value should be either jdk or native")
  }

  import NettyServer._

  override def mode: Mode = config.mode

  /**
   * The event loop
   */
  private val eventLoop = {
    val threadFactory = NamedThreadFactory("netty-event-loop")
    transport match {
      case Native => new EpollEventLoopGroup(threadCount, threadFactory)
      case Jdk    => new NioEventLoopGroup(threadCount, threadFactory)
    }
  }

  /**
   * A reference to every channel, both server and incoming, this allows us to shutdown cleanly.
   */
  private val allChannels = new DefaultChannelGroup(eventLoop.next())

  private def setOptions(
      setOption: (ChannelOption[AnyRef], AnyRef) => Any,
      config: Config,
      bootstrapping: Boolean = false
  ) = {
    def unwrap(value: ConfigValue) = value.unwrapped() match {
      case number: Number => number.intValue().asInstanceOf[Integer]
      case other          => other
    }
    config.entrySet().asScala.filterNot(_.getKey.startsWith("child.")).foreach { option =>
      val cleanKey = option.getKey.stripPrefix("\"").stripSuffix("\"")
      if (ChannelOption.exists(cleanKey)) {
        logger.debug(s"Setting Netty channel option ${cleanKey} to ${unwrap(option.getValue)}${if (bootstrapping) {
          " at bootstrapping"
        } else {
          ""
        }}")
        setOption(ChannelOption.valueOf(cleanKey), unwrap(option.getValue))
      } else {
        logger.warn("Ignoring unknown Netty channel option: " + cleanKey)
        transport match {
          case Native =>
            logger.warn(
              "Valid values can be found at http://netty.io/4.1/api/io/netty/channel/ChannelOption.html, " +
                "https://netty.io/4.1/api/io/netty/channel/unix/UnixChannelOption.html and " +
                "http://netty.io/4.1/api/io/netty/channel/epoll/EpollChannelOption.html"
            )
          case Jdk =>
            logger.warn("Valid values can be found at http://netty.io/4.1/api/io/netty/channel/ChannelOption.html")
        }
      }
    }
  }

  /**
   * Bind to the given address, returning the server channel, and a stream of incoming connection channels.
   */
  private def bind(address: InetSocketAddress): (Channel, Source[Channel, _]) = {
    val serverChannelEventLoop = eventLoop.next

    // Watches for channel events, and pushes them through a reactive streams publisher.
    val channelPublisher = new HandlerPublisher(serverChannelEventLoop, classOf[Channel])

    val channelClass = transport match {
      case Native => classOf[EpollServerSocketChannel]
      case Jdk    => classOf[NioServerSocketChannel]
    }

    val bootstrap = new Bootstrap()
      .channel(channelClass)
      .group(serverChannelEventLoop)
      .option(ChannelOption.AUTO_READ, java.lang.Boolean.FALSE) // publisher does ctx.read()
      .handler(channelPublisher)
      .localAddress(address)

    setOptions(bootstrap.option, bootstrapOption, true)

    val channel = bootstrap.bind.await().channel()
    allChannels.add(channel)

    (channel, Source.fromPublisher(channelPublisher))
  }

  /**
   * Create a sink for the incoming connection channels.
   */
  private def channelSink(port: Int): Sink[Channel, Future[Done]] = {
    Sink.foreach[Channel] { (connChannel: Channel) =>
      // Setup the channel for explicit reads
      connChannel.config().setOption(ChannelOption.AUTO_READ, java.lang.Boolean.FALSE)

      setOptions(connChannel.config().setOption, channelOption)

      val pipeline = connChannel.pipeline()

      // Netty HTTP decoders/encoders/etc
      pipeline.addLast("decoder", new HttpRequestDecoder(maxInitialLineLength, maxHeaderSize, maxChunkSize))
      pipeline.addLast("encoder", new HttpResponseEncoder())
      pipeline.addLast("decompressor", new HttpContentDecompressor())
      if (logWire) {
        pipeline.addLast("logging", new LoggingHandler(LogLevel.DEBUG))
      }

      val idleTimeout = httpIdleTimeout
      if (idleTimeout != Duration.Inf) {
        logger.trace(s"using idle timeout of $idleTimeout on port $port")
        // only timeout if both reader and writer have been idle for the specified time
        pipeline.addLast("idle-handler", new IdleStateHandler(0, 0, idleTimeout.length, idleTimeout.unit))
      }

      val requestHandler = new PlayRequestHandler(this, maxContentLength, application)

      // Use the streams handler to close off the connection.
      pipeline.addLast("http-handler", new HttpStreamsServerHandler(Seq[ChannelHandler](requestHandler).asJava))

      pipeline.addLast("request-handler", requestHandler)

      // And finally, register the channel with the event loop
      val childChannelEventLoop = eventLoop.next()
      childChannelEventLoop.register(connChannel)
      allChannels.add(connChannel)
    }
  }

  // the HTTP server channel
  private val httpChannel = bindChannel(config.port)

  private def bindChannel(port: Int): Channel = {
    val protocolName                   = "HTTP"
    val address                        = new InetSocketAddress(config.address, port)
    val (serverChannel, channelSource) = bind(address)
    channelSource.runWith(channelSink(port = port))
    val boundAddress = serverChannel.localAddress()
    if (boundAddress == null) {
      val e = new ServerListenException(protocolName, address)
      logger.error(e.getMessage)
      throw e
    }
    if (mode != Mode.Test) {
      logger.info(s"Listening for $protocolName on $boundAddress")
    }
    serverChannel
  }

  override def stop(): Unit = CoordinatedShutdownSupport.syncShutdown(actorSystem, ServerStoppedReason)

  // Using CoordinatedShutdown means that instead of invoking code imperatively in `stop`
  // we have to register it as early as possible as CoordinatedShutdown tasks and
  // then `stop` runs CoordinatedShutdown.
  private def registerShutdownTasks(): Unit = {
    implicit val ctx: ExecutionContext = actorSystem.dispatcher

    val cs = CoordinatedShutdown(actorSystem)
    cs.addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "trace-server-stop-request") { () =>
      mode match {
        case Mode.Test =>
        case _         => logger.info("Stopping server...")
      }
      Future.successful(Done)
    }

    val unbindTimeout = cs.timeout(CoordinatedShutdown.PhaseServiceUnbind)
    cs.addTask(CoordinatedShutdown.PhaseServiceUnbind, "netty-server-unbind") { () =>
      // First, close all opened sockets
      allChannels.close().awaitUninterruptibly(unbindTimeout.toMillis - 100)
      // Now shutdown the event loop
      eventLoop.shutdownGracefully().await(unbindTimeout.toMillis - 100)
      Future.successful(Done)
    }

    // Call provided hook
    // Do this last because the hooks were created before the server,
    // so the server might need them to run until the last moment.
    cs.addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "user-provided-server-stop-hook") { () =>
      logger.info("Running provided shutdown stop hooks")
      stopHook().map(_ => Done)
    }
  }

  private def initializeChannelOptionsStaticMembers(): Unit = {
    // Workaround to make sure that various *ChannelOption classes (and therefore their static members) get initialized.
    // The static members of these *ChannelOption classes get initialized by calling ChannelOption.valueOf(...).
    // ChannelOption.valueOf(...) saves the name of the channel option into a pool/map.
    // ChannelOption.exists(...) just checks that pool/map, meaning if a class wasn't initialized before,
    // that method is not able to find a channel option (even though that option "exists" and should be found).
    // We bumped into this problem when setting a native socket transport option into the config path
    // play.server.netty.option { ... }
    // (But not when setting it into the "child" sub-path!)

    // How to force a class to get initialized:
    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-12.html#jls-12.4.1
    Seq(classOf[ChannelOption[_]], classOf[UnixChannelOption[_]], classOf[EpollChannelOption[_]]).foreach(clazz => {
      logger.debug(s"Class ${clazz.getName} will be initialized (if it hasn't been initialized already)")
      Class.forName(clazz.getName)
    })
  }

  override lazy val mainAddress: InetSocketAddress = {
    httpChannel.localAddress().asInstanceOf[InetSocketAddress]
  }

  val serverEndpoint = ServerEndpoint(
    description = "Netty HTTP/1.1 (plaintext)",
    scheme = "http",
    host = config.address,
    port = httpChannel.localAddress().asInstanceOf[InetSocketAddress].getPort,
    protocols = Set(HttpProtocol.HTTP_1_0, HttpProtocol.HTTP_1_1),
    ssl = None
  )
}

/**
 * Create a Netty server zfrom a given router using [[BuiltInComponents]]:
 *
 * {{{
 *   val server = NettyServer.fromRouterWithComponents(ServerConfig(port = Some(9002))) { components =>
 *     import play.api.mvc.Results._
 *     import components.{ defaultActionBuilder => Action }
 *     {
 *       case GET(p"/") => Action {
 *         Ok("Hello")
 *       }
 *     }
 *   }
 * }}}
 *
 * Use this together with <a href="https://www.playframework.com/documentation/latest/ScalaSirdRouter">Sird Router</a>.
 */
object NettyServer {
  private val logger = Logger(this.getClass)
}

/**
 * Cake for building a simple Netty server.
 */
trait NettyServerComponents extends ServerComponents {
  lazy val server: NettyServer = {
    // Start the application first
    Play.start(application)
    new NettyServer(serverConfig, application, serverStopHook, application.actorSystem)(
      application.materializer
    )
  }

  def application: Application
}

/**
 * A convenient helper trait for constructing an NettyServer, for example:
 *
 * {{{
 *   val components = new DefaultNettyServerComponents {
 *     override lazy val router = {
 *       case GET(p"/") => Action(parse.json) { body =>
 *         Ok("Hello")
 *       }
 *     }
 *   }
 *   val server = components.server
 * }}}
 */
trait DefaultNettyServerComponents extends NettyServerComponents with BuiltInComponents with NoHttpFiltersComponents
