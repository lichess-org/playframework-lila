/*
 * Copyright 2018 Greg Methvin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.methvin.play.autoconfig

import scala.annotation.StaticAnnotation

/** Used to mark a non-primary constructor as the constructor to use to instantiate the configuration class.
  */
final class ConfigConstructor extends StaticAnnotation



case class Config(a: Int, b: String)
val config = Config(42, "ha")

// val _ = AutoConfig.fun(config)
val _ = AutoConfig.loader[Config]
