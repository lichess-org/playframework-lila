package io.methvin.play.autoconfig

import play.api.ConfigLoader

// based on https://github.com/gmethvin/play-autoconfig
object AutoConfig {

  /** Generate a `ConfigLoader[T]` calling the constructor of a class. Use [[ConfigConstructor]] and [[ConfigName]]
   * annotations to change the constructor to use and the names of the parameters.
   *
   * @tparam T the type for which to create the configuration class
   * @return an instance of the `ConfigLoader` for the given class.
   */
  inline def loader[T]: ConfigLoader[T] = ${AutoConfigImpl.loader[T]}

  // inline def fun[A](inline x: A): A = ${ AutoConfigImpl.fun('x) }

  // inline def power[A](inline x: Double, n: Int): Double = ${ AutoConfigImpl.powerCode('x, 'n) }
  // inline def debugPower[A](inline x: Double, n: Int): Double = ${ AutoConfigImpl.debugPowerCode('x, 'n) }

  // given derived[T: Type](using Quotes): Expr[ConfigLoader[T]] = ???
}
