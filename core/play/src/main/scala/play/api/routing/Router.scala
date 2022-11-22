/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.routing

import play.api.libs.typedmap.TypedKey
import play.api.Configuration
import play.api.Environment
import play.api.mvc.Handler
import play.api.mvc.RequestHeader
import play.api.routing.Router.Routes
import play.utils.Reflect

/**
 * A router.
 */
trait Router {
  self =>

  /**
   * The actual routes of the router.
   */
  def routes: Router.Routes

  /**
   * Get a new router that routes requests to `s"$prefix/$path"` in the same way this router routes requests to `path`.
   *
   * @return the prefixed router
   */
  def withPrefix(prefix: String): Router

  /**
   * An alternative syntax for `withPrefix`. For example:
   *
   * {{{
   *   val router = "/bar" /: barRouter
   * }}}
   */
  final def /:(prefix: String): Router = withPrefix(prefix)

  /**
   * A lifted version of the routes partial function.
   */
  final def handlerFor(request: RequestHeader): Option[Handler] = routes.lift(request)

  /**
   * Compose two routers into one. The resulting router will contain
   * both the routes in `this` as well as `router`
   */
  final def orElse(other: Router): Router = new Router {
    def withPrefix(prefix: String): Router           = self.withPrefix(prefix).orElse(other.withPrefix(prefix))
    def routes: Routes                               = self.routes.orElse(other.routes)
  }
}

/**
 * Utilities for routing.
 */
object Router {

  /**
   * The type of the routes partial function
   */
  type Routes = PartialFunction[RequestHeader, Handler]

  /**
   * Try to load the configured router class.
   *
   * @return The router class if configured or if a default one in the root package was detected.
   */
  def load(env: Environment, configuration: Configuration): Option[Class[? <: Router]] = {
    val className = configuration.getDeprecated[Option[String]]("play.http.router", "application.router")

    try {
      Some(Reflect.getClass[Router](className.getOrElse("router.Routes"), env.classLoader))
    } catch {
      case e: ClassNotFoundException =>
        // Only throw an exception if a router was explicitly configured, but not found.
        // Otherwise, it just means this application has no router, and that's ok.
        className.map { routerName =>
          throw configuration.reportError("application.router", s"Router not found: $routerName")
        }
    }
  }

  /**
   * Request attributes used by the router.
   */
  object Attrs {

    val ActionName: TypedKey[String] = TypedKey("ActionName")
  }

  /**
   * Create a new router from the given partial function
   *
   * @param routes The routes partial function
   * @return A router that uses that partial function
   */
  def from(routes: Router.Routes): Router = SimpleRouter(routes)

  /**
   * An empty router.
   *
   * Never returns an handler from the routes function.
   */
  val empty: Router = new Router {
    def withPrefix(prefix: String) = this
    def routes                     = PartialFunction.empty
  }

  /**
   * Concatenate another prefix with an existing prefix, collapsing extra slashes. If the existing prefix is empty or
   * "/" then the new prefix replaces the old one. Otherwise the new prefix is prepended to the old one with a slash in
   * between, ignoring a final slash in the new prefix or an initial slash in the existing prefix.
   */
  def concatPrefix(newPrefix: String, existingPrefix: String): String = {
    if (existingPrefix.isEmpty || existingPrefix == "/") {
      newPrefix
    } else {
      newPrefix.stripSuffix("/") + "/" + existingPrefix.stripPrefix("/")
    }
  }
}

/**
 * A simple router that implements the withPrefix for you.
 */
trait SimpleRouter extends Router { self =>
  def withPrefix(prefix: String): Router = {
    if (prefix == "/") self
    else {
      val prefixTrailingSlash = if (prefix.endsWith("/")) prefix else prefix + "/"
      val prefixed: PartialFunction[RequestHeader, RequestHeader] = {
        case rh: RequestHeader if rh.path == prefix || rh.path.startsWith(prefixTrailingSlash) =>
          val newPath = "/" + rh.path.drop(prefixTrailingSlash.length)
          rh.withTarget(rh.target.withPath(newPath))
      }
      new Router {
        def routes                = Function.unlift(prefixed.lift.andThen(_.flatMap(self.routes.lift)))
        def withPrefix(p: String) = self.withPrefix(Router.concatPrefix(p, prefix))
      }
    }
  }
}

class SimpleRouterImpl(routesProvider: => Router.Routes) extends SimpleRouter {
  def routes = routesProvider
}

object SimpleRouter {

  /**
   * Create a new simple router from the given routes
   */
  def apply(routes: Router.Routes): Router = new SimpleRouterImpl(routes)
}
