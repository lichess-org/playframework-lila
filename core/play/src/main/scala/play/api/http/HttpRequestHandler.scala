/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.http

import javax.inject.Inject
import javax.inject.Provider

import play.api.http.Status.*
import play.api.inject.Binding
import play.api.inject.BindingKey
import play.api.libs.streams.Accumulator
import play.api.mvc.*
import play.api.routing.Router
import play.api.Configuration
import play.api.Environment
import play.utils.Reflect

/**
 * Primary entry point for all HTTP requests on Play applications.
 */
trait HttpRequestHandler {

  /**
   * Get a handler for the given request.
   *
   * In addition to retrieving a handler for the request, the request itself may be modified - typically it will be
   * tagged with routing information.  It is also acceptable to simply return the request as is.  Play will switch to
   * using the returned request from this point in in its request handling.
   *
   * The reason why the API allows returning a modified request, rather than just wrapping the Handler in a new Handler
   * that modifies the request, is so that Play can pass this request to other handlers, such as error handlers, or
   * filters, and they will get the tagged/modified request.
   *
   * @param request The request to handle
   * @return The possibly modified/tagged request, and a handler to handle it
   */
  def handlerForRequest(request: RequestHeader): (RequestHeader, Handler)
}

object HttpRequestHandler {
  def bindingsFromConfiguration(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    Reflect.bindingsFromConfiguration[
      HttpRequestHandler,
      DefaultHttpRequestHandler
    ](environment, configuration, "play.http.requestHandler", "RequestHandler")
  }
}


/**
 * Implementation of a [HttpRequestHandler] that always returns NotImplemented results
 */
object NotImplementedHttpRequestHandler extends HttpRequestHandler {
  def handlerForRequest(request: RequestHeader) =
    request -> EssentialAction(_ => Accumulator.done(Results.NotImplemented))
}

/**
 * A base implementation of the [[HttpRequestHandler]] that handles Scala actions. If you use Java actions in your
 * application, you should override [[JavaCompatibleHttpRequestHandler]]; otherwise you can override this for your
 * custom handler.
 *
 * Technically, this is not the default request handler that Play uses, rather, the [[JavaCompatibleHttpRequestHandler]]
 * is the default one, in order to provide support for Java actions.
 */
class DefaultHttpRequestHandler(
    router: Provider[Router],
    errorHandler: HttpErrorHandler,
    configuration: HttpConfiguration,
    filters: Seq[EssentialFilter]
) extends HttpRequestHandler {
  @Inject
  def this(
      router: Provider[Router],
      errorHandler: HttpErrorHandler,
      configuration: HttpConfiguration,
      filters: HttpFilters
  ) = {
    this(router, errorHandler, configuration, filters.filters)
  }

  @deprecated("Use the main DefaultHttpRequestHandler constructor", "2.9.0")
  def this(
      router: Router,
      errorHandler: HttpErrorHandler,
      configuration: HttpConfiguration,
      filters: Seq[EssentialFilter]
  ) = {
    this(() => router, errorHandler, configuration, filters)
  }

  @deprecated("Use the main DefaultHttpRequestHandler constructor", "2.9.0")
  def this(
      router: Router,
      errorHandler: HttpErrorHandler,
      configuration: HttpConfiguration,
      filters: HttpFilters
  ) = {
    this(() => router, errorHandler, configuration, filters.filters)
  }

  private val context = configuration.context.stripSuffix("/")

  /** Work out whether a path is handled by this application. */
  private def inContext(path: String): Boolean = {
    // Assume context is a string without a trailing '/'.
    // Handle four cases:
    // * context.isEmpty
    //   - There is no context, everything is in context, short circuit all other checks
    // * !path.startsWith(context)
    //   - Either path is shorter than context or starts with a different prefix.
    // * path.startsWith(context) && path.length == context.length
    //   - Path is equal to context.
    // * path.startsWith(context) && path.charAt(context.length) == '/')
    //   - Path starts with context followed by a '/' character.
    context.isEmpty ||
    (path.startsWith(context) && (path.length == context.length || path.charAt(context.length) == '/'))
  }

  override def handlerForRequest(request: RequestHeader): (RequestHeader, Handler) = {
    def handleWithStatus(status: Int) =
      ActionBuilder.ignoringBody.async(BodyParsers.utils.empty)(req => errorHandler.onClientError(req, status))

    /**
     * Call the router to get the handler, but with a couple of types of fallback.
     * First, if a HEAD request isn't explicitly routed try routing it as a GET
     * request. Second, if no routing information is present, fall back to a 404
     * error.
     */
    def routeWithFallback(request: RequestHeader): Handler = {
      routeRequest(request).getOrElse {
        request.method match {
          // We automatically permit HEAD requests against any GETs without the need to
          // add an explicit mapping in Routes. Since we couldn't route the HEAD request,
          // try to get a Handler for the equivalent GET request instead. Notes:
          // 1. The handler returned will still be passed a HEAD request when it is
          //    actually evaluated.
          case HttpVerbs.HEAD => {
            routeRequest(request.withMethod(HttpVerbs.GET)) match {
              case Some(handler: Handler) => handler
              case None => handleWithStatus(NOT_FOUND)
            }
          }
          case _ =>
            // An Action for a 404 error
            handleWithStatus(NOT_FOUND)
        }
      }
    }

    // 1. Query the router to get a handler
    // 2. Resolve handlers that preprocess the request
    // 3. Modify the handler to do filtering, if necessary
    // 4. Again resolve any handlers that do preprocessing
    val routedHandler                              = routeWithFallback(request)
    val (preprocessedRequest, preprocessedHandler) = Handler.applyStages(request, routedHandler)
    val filteredHandler                            = filterHandler(preprocessedRequest, preprocessedHandler)
    val (preprocessedPreprocessedRequest, preprocessedFilteredHandler) =
      Handler.applyStages(preprocessedRequest, filteredHandler)
    (preprocessedPreprocessedRequest, preprocessedFilteredHandler)
  }

  /**
   * Update the given handler so that when the handler is run any filters will also be run. The
   * default behavior is to wrap all [[play.api.mvc.EssentialAction]]s by calling `filterAction`, but to leave
   * other kinds of handlers unchanged.
   */
  protected def filterHandler(request: RequestHeader, handler: Handler): Handler = {
    handler match {
      case action: EssentialAction if inContext(request.path) => filterAction(action)
      case handler                                            => handler
    }
  }

  /**
   * Apply filters to the given action.
   */
  protected def filterAction(next: EssentialAction): EssentialAction = {
    filters.foldRight(next)(_.apply(_))
  }

  /**
   * Called when an HTTP request has been received.
   *
   * The default is to use the application router to find the appropriate action.
   *
   * This method can be overridden if you want to provide some custom routing strategies, for example, using different
   * routers based on various request parameters.
   *
   * @param request The request
   * @return A handler to handle the request, if one can be found
   */
  def routeRequest(request: RequestHeader): Option[Handler] = {
    router.get().handlerFor(request)
  }
}
