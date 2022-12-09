/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.core.server

import java.util.function.{ Function => JFunction }
import akka.actor.CoordinatedShutdown
import akka.annotation.ApiMayChange
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import play.api.ApplicationLoader.Context
import play.api._
import play.api.http.HttpErrorHandler
import play.api.http.Port
import play.api.inject.ApplicationLifecycle
import play.api.inject.DefaultApplicationLifecycle
import play.api.libs.streams.Accumulator
import play.api.mvc._
import play.api.routing.Router
import play.core._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try
import play.api.http.DefaultHttpErrorHandler

/**
 * Provides generic server behaviour for Play applications.
 */
trait Server {
  def mode: Mode

  def applicationProvider: ApplicationProvider

  def stop(): Unit = {
  }

  /**
   * Get the address of the server.
   *
   * @return The address of the server.
   */
  def mainAddress: java.net.InetSocketAddress

  /**
   * Returns the HTTP port of the server.
   *
   * This is useful when the port number has been automatically selected (by setting a port number of 0).
   *
   * @return The HTTP port the server is bound to, if the HTTP connector is enabled.
   */
  def httpPort: Option[Int] = serverEndpoints.httpEndpoint.map(_.port)

  /**
   * Returns the HTTPS port of the server.
   *
   * This is useful when the port number has been automatically selected (by setting a port number of 0).
   *
   * @return The HTTPS port the server is bound to, if the HTTPS connector is enabled.
   */
  def httpsPort: Option[Int] = serverEndpoints.httpsEndpoint.map(_.port)

  /**
   * Endpoints information for this server.
   */
  @ApiMayChange
  def serverEndpoints: ServerEndpoints
}

/**
 * Utilities for creating a server that runs around a block of code.
 */
object Server {

  /**
   * Try to get the handler for a request and return it as a `Right`. If we
   * can't get the handler for some reason then return a result immediately
   * as a `Left`. Reasons to return a `Left` value:
   *
   * - If there's a "web command" installed that intercepts the request.
   * - If we fail to get the `Application` from the `applicationProvider`,
   *   i.e. if there's an error loading the application.
   * - If an exception is thrown.
   */
  private[server] def getHandlerFor(
      request: RequestHeader,
      tryApp: Try[Application],
  ): (RequestHeader, Handler) = {
    @inline def handleErrors(
        errorHandler: HttpErrorHandler,
        req: RequestHeader
    ): PartialFunction[Throwable, (RequestHeader, Handler)] = {
      case e: ThreadDeath         => throw e
      case e: VirtualMachineError => throw e
      case e: Throwable =>
        val errorResult = errorHandler.onServerError(req, e)
        val errorAction = actionForResult(errorResult)
        (req, errorAction)
    }

    try {
      // Get the Application from the try.
      val application = tryApp.get
      // We managed to get an Application, now make a fresh request using the Application's RequestFactory.
      // The request created by the request factory needs to be at this scope so that it can be
      // used by application error handler. The reason for that is that this request is populated
      // with all attributes necessary to translate it to Java.
      // TODO: `copyRequestHeader` is a misleading name here since it is also populating the request with attributes
      //       such as id, session, flash, etc.
      val enrichedRequest: RequestHeader = application.requestFactory.copyRequestHeader(request)
      try {
        // We hen use the Application's logic to handle that request.
        val (handlerHeader, handler) = application.requestHandler.handlerForRequest(enrichedRequest)
        (handlerHeader, handler)
      } catch {
        handleErrors(application.errorHandler, enrichedRequest)
      }
    } catch {
      handleErrors(DefaultHttpErrorHandler, request)
    }
  }

  /**
   * Create a simple [[Handler]] which sends a [[Result]].
   */
  private[server] def actionForResult(errorResult: Future[Result]): Handler = {
    EssentialAction(_ => Accumulator.done(errorResult))
  }

  /**
   * Parses the config setting `infinite` as `Long.MaxValue` otherwise uses Config's built-in
   * parsing of byte values.
   */
  private[server] def getPossiblyInfiniteBytes(
      config: Config,
      path: String,
      deprecatedPath: String = """"""""
  ): Long = {
    Configuration(config).getDeprecated[String](path, deprecatedPath) match {
      case "infinite" => Long.MaxValue
      case _          => config.getBytes(if (config.hasPath(deprecatedPath)) deprecatedPath else path)
    }
  }

  case object ServerStoppedReason extends CoordinatedShutdown.Reason
}

/**
 * Components to create a Server instance.
 */
trait ServerComponents {
  def server: Server

  lazy val serverConfig: ServerConfig = ServerConfig()

  lazy val environment: Environment                   = Environment.simple(mode = serverConfig.mode)
  lazy val configuration: Configuration               = Configuration(ConfigFactory.load())
  lazy val applicationLifecycle: ApplicationLifecycle = new DefaultApplicationLifecycle

  def serverStopHook: () => Future[Unit] = () => Future.successful(())
}

/**
 * Define how to create a Server from a Router.
 */
private[server] trait ServerFromRouter {
  protected def createServerFromRouter(serverConfig: ServerConfig = ServerConfig())(
      routes: ServerComponents with BuiltInComponents => Router
  ): Server

  /**
   * Creates a [[Server]] from the given router.
   *
   * @param config the server configuration
   * @param routes the routes definitions
   * @return an AkkaHttpServer instance
   */
  @deprecated(
    "Use fromRouterWithComponents or use DefaultAkkaHttpServerComponents/DefaultNettyServerComponents",
    "2.7.0"
  )
  def fromRouter(config: ServerConfig = ServerConfig())(routes: PartialFunction[RequestHeader, Handler]): Server = {
    createServerFromRouter(config) { _ =>
      Router.from(routes)
    }
  }

  /**
   * Creates a [[Server]] from the given router, using [[ServerComponents]].
   *
   * @param config the server configuration
   * @param routes the routes definitions
   * @return an AkkaHttpServer instance
   */
  def fromRouterWithComponents(
      config: ServerConfig = ServerConfig()
  )(routes: BuiltInComponents => PartialFunction[RequestHeader, Handler]): Server = {
    createServerFromRouter(config)(components => Router.from(routes(components)))
  }
}
