/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.routes.compiler.templates

import org.specs2.mutable.Specification
import play.routes.compiler._

class TemplatesSpec extends Specification {

  def route(staticPath: String, params: Seq[Parameter] = Nil): Route = {
    Route(
      HttpVerb("GET"),
      PathPattern(Seq(StaticPart(staticPath))),
      HandlerCall(Option("pkg"), "ctrl", true, "method", Some(params))
    )
  }
}
