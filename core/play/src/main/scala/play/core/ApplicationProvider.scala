/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.core

import java.io.*
import scala.util.Try
import scala.util.Success

import play.api.*
import play.api.mvc.*

/**
 * Provides information about a Play Application running inside a Play server.
 */
trait ApplicationProvider {

  /**
   * Get the application. In dev mode this lazily loads the application.
   *
   * NOTE: This should be called once per request. Calling multiple times may result in multiple compilations.
   */
  def get: Try[Application]
}

object ApplicationProvider {

  /**
   * Creates an ApplicationProvider that wraps an Application instance.
   */
  def apply(application: Application) = new ApplicationProvider {
    val get: Try[Application] = Success(application)
  }
}
