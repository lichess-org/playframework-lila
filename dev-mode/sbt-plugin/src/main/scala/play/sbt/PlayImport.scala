/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.sbt

import sbt._

import play.dev.filewatch.FileWatchService

/**
 * Declares the default imports for Play plugins.
 */
object PlayImport {
  val Production = config("production")

  def component(id: String) = "com.typesafe.play" %% id % play.core.PlayVersion.current

  def movedExternal(msg: String): ModuleID = {
    System.err.println(msg)
    class ComponentExternalisedException extends RuntimeException(msg) with FeedbackProvidedException
    throw new ComponentExternalisedException
  }

  val playCore = component("play")

  val nettyServer = component("play-netty-server")

  val akkaHttpServer = component("play-akka-http-server")

  val logback = component("play-logback")

  val evolutions = component("play-jdbc-evolutions")

  val jdbc = component("play-jdbc")

  def anorm =
    movedExternal(
      """Anorm has been moved to an external module.
        |See https://playframework.com/documentation/2.4.x/Migration24 for details.""".stripMargin
    )

  val javaCore = component("play-java")

  val javaForms = component("play-java-forms")

  val jodaForms = component("play-joda-forms")

  val filters = component("filters-helpers")

  // Integration with JSR 107
  val jcache = component("play-jcache")

  val cacheApi = component("play-cache")

  val ehcache = component("play-ehcache")

  val caffeine = component("play-caffeine-cache")

  def json = movedExternal("""play-json module has been moved to a separate project.
                             |See https://playframework.com/documentation/2.6.x/Migration26 for details.""".stripMargin)

  val guice = component("play-guice")

  val ws = component("play-ahc-ws")

  // alias javaWs to ws
  val javaWs = ws

  val clusterSharding     = component("play-cluster-sharding")
  val javaClusterSharding = component("play-java-cluster-sharding")

  object PlayKeys {
    val playDefaultPort    = SettingKey[Int]("playDefaultPort", "The default port that Play runs on")
    val playDefaultAddress = SettingKey[String]("playDefaultAddress", "The default address that Play runs on")

    /** A hook to configure how play blocks on user input while running. */
    val playInteractionMode =
      SettingKey[PlayInteractionMode]("playInteractionMode", "Hook to configure how Play blocks when running")

    val externalizeResources = SettingKey[Boolean](
      "playExternalizeResources",
      "Whether resources should be externalized into the conf directory when Play is packaged as a distribution."
    )
    val playExternalizedResources =
      TaskKey[Seq[(File, String)]]("playExternalizedResources", "The resources to externalize")
    val externalizeResourcesExcludes = SettingKey[Seq[File]](
      "externalizeResourcesExcludes",
      "Resources that should not be externalized but stay in the generated jar"
    )
    val playJarSansExternalized =
      TaskKey[File]("playJarSansExternalized", "Creates a jar file that has all the externalized resources excluded")

    val playPlugin = SettingKey[Boolean]("playPlugin")

    val devSettings = SettingKey[Seq[(String, String)]]("playDevSettings")

    val assetsPrefix      = SettingKey[String]("assetsPrefix")

    val playMonitoredFiles = TaskKey[Seq[File]]("playMonitoredFiles")
    val fileWatchService =
      SettingKey[FileWatchService]("fileWatchService", "The watch service Play uses to watch for file changes")
  }
}
