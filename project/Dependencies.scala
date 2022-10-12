/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._
import Keys._

import buildinfo.BuildInfo

object Dependencies {
  val akkaVersion: String = sys.props.getOrElse("akka.version", "2.6.20")
  val akkaHttpVersion     = sys.props.getOrElse("akka.http.version", "10.2.7")

  val playJsonVersion = "2.10.0-RC6"

  val logback = "ch.qos.logback" % "logback-classic" % "1.4.1"

  val specs2Version = "4.17.0"
  val specs2CoreDeps = Seq(
    "specs2-core",
    "specs2-junit"
  ).map("org.specs2" %% _ % specs2Version)
  val specs2Deps = specs2CoreDeps ++ Seq(
    "specs2-mock"
  ).map("org.specs2" %% _ % specs2Version)

  val specsMatcherExtra = "org.specs2" %% "specs2-matcher-extra" % specs2Version

  val scalacheckDependencies = Seq(
    "org.specs2"     %% "specs2-scalacheck" % specs2Version % Test,
    "org.scalacheck" %% "scalacheck"        % "1.16.0"      % Test
  )

  val jacksonVersion  = "2.13.4"
  val jacksonDatabind = Seq("com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion)
  val jacksons = Seq(
    "com.fasterxml.jackson.core"     % "jackson-core",
    "com.fasterxml.jackson.core"     % "jackson-annotations",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8",
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"
  ).map(_ % jacksonVersion) ++ jacksonDatabind
  // Overrides additional jackson deps pulled in by akka-serialization-jackson
  // https://github.com/akka/akka/blob/v2.6.19/project/Dependencies.scala#L129-L137
  // https://github.com/akka/akka/blob/b08a91597e26056d9eea4a216e745805b9052a2a/build.sbt#L257
  // Can be removed as soon as akka upgrades to same jackson version like Play uses
  val akkaSerializationJacksonOverrides = Seq(
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor",
    "com.fasterxml.jackson.module"     % "jackson-module-parameter-names",
    "com.fasterxml.jackson.module"     %% "jackson-module-scala",
  ).map(_ % jacksonVersion)

  val playJson = "com.typesafe.play" %% "play-json" % playJsonVersion

  val slf4jVersion = "2.0.0"
  val slf4j        = Seq("slf4j-api", "jul-to-slf4j", "jcl-over-slf4j").map("org.slf4j" % _ % slf4jVersion)
  val slf4jSimple  = "org.slf4j" % "slf4j-simple" % slf4jVersion

  val guava      = "com.google.guava"         % "guava"        % "31.1-jre"
  val findBugs   = "com.google.code.findbugs" % "jsr305"       % "3.0.2" // Needed by guava
  val mockitoAll = "org.mockito"              % "mockito-core" % "4.8.0"

  def scalaParserCombinators(scalaVersion: String) =
    Seq("org.scala-lang.modules" %% "scala-parser-combinators" % {
      CrossVersion.partialVersion(scalaVersion) match {
        case Some((2, _)) => "1.1.2"
        case _            => "2.1.1"
      }
    })

  val springFrameworkVersion = "5.3.22"

  val joda = Seq(
    "joda-time" % "joda-time"    % "2.11.2",
    "org.joda"  % "joda-convert" % "2.2.2"
  )

  val junitInterface = "com.github.sbt" % "junit-interface" % "0.13.3"
  val junit          = "junit"          % "junit"           % "4.13.2"

  val guiceVersion = "5.1.0"
  val guiceDeps = Seq(
    "com.google.inject"            % "guice"                % guiceVersion,
    "com.google.inject.extensions" % "guice-assistedinject" % guiceVersion
  )

  def runtime(scalaVersion: String) =
    slf4j ++
      Seq("akka-actor", "akka-actor-typed", "akka-slf4j", "akka-serialization-jackson")
        .map("com.typesafe.akka" %% _ % akkaVersion) ++
      Seq("akka-testkit", "akka-actor-testkit-typed")
        .map("com.typesafe.akka" %% _ % akkaVersion % Test) ++
      jacksons ++
      akkaSerializationJacksonOverrides ++
      Seq(
        playJson,
        guava,
        "javax.inject"                                              % "javax.inject" % "1",
      ) ++ scalaParserCombinators(scalaVersion) ++ specs2Deps.map(_ % Test)

  val nettyVersion = "4.1.81.Final"

  val netty = Seq(
    "com.typesafe.netty" % "netty-reactive-streams-http" % "2.0.6",
    ("io.netty" % "netty-transport-native-epoll" % nettyVersion).classifier("linux-x86_64")
  ) ++ specs2Deps.map(_ % Test)

  val akkaHttp = "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion

  val akkaHttp2Support = "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion

  val cookieEncodingDependencies = slf4j

  val jimfs = "com.google.jimfs" % "jimfs" % "1.2"

  def routesCompilerDependencies(scalaVersion: String) = {
    specs2CoreDeps.map(_ % Test) ++ Seq(specsMatcherExtra % Test) ++ scalaParserCombinators(scalaVersion) ++ (logback % Test :: Nil)
  }

  private def sbtPluginDep(moduleId: ModuleID, sbtVersion: String, scalaVersion: String) = {
    Defaults.sbtPluginExtra(
      moduleId,
      CrossVersion.binarySbtVersion(sbtVersion),
      CrossVersion.binaryScalaVersion(scalaVersion)
    )
  }

  val typesafeConfig = "com.typesafe" % "config" % "1.4.2"

  def sbtDependencies(sbtVersion: String, scalaVersion: String) = {
    def sbtDep(moduleId: ModuleID) = sbtPluginDep(moduleId, sbtVersion, scalaVersion)

    Seq(
      typesafeConfig,
      slf4jSimple,
      sbtDep("com.typesafe.play" % "sbt-twirl"           % BuildInfo.sbtTwirlVersion),
      sbtDep("com.github.sbt"    % "sbt-native-packager" % BuildInfo.sbtNativePackagerVersion),
      sbtDep("com.typesafe.sbt"  % "sbt-web"             % "1.4.4"),
      logback             % Test
    ) ++ specs2Deps.map(_ % Test)
  }

  val streamsDependencies = Seq(
    "org.reactivestreams"   % "reactive-streams" % "1.0.4",
    "com.typesafe.akka"     %% "akka-stream" % akkaVersion,
  ) ++ specs2CoreDeps.map(_ % Test)

  val playServerDependencies = specs2Deps.map(_ % Test) ++ Seq(
    guava   % Test,
    logback % Test
  )

  val caffeineVersion = "3.0.1"
  val playCaffeineDeps = Seq(
    "com.github.ben-manes.caffeine" % "caffeine" % caffeineVersion,
    "com.github.ben-manes.caffeine" % "jcache"   % caffeineVersion
  )

  val playWsStandaloneVersion = "2.2.0-M2"
  val playWsDeps = Seq(
    "com.typesafe.play" %% "play-ws-standalone"      % playWsStandaloneVersion,
    "com.typesafe.play" %% "play-ws-standalone-xml"  % playWsStandaloneVersion,
    "com.typesafe.play" %% "play-ws-standalone-json" % playWsStandaloneVersion,
    // Update transitive Akka version as needed:
    "com.typesafe.akka"                        %% "akka-stream" % akkaVersion
  ) ++ (specs2Deps :+ specsMatcherExtra).map(_ % Test) :+ mockitoAll % Test

  // Must use a version of ehcache that supports jcache 1.0.0
  val playAhcWsDeps = Seq(
    "com.typesafe.play"             %% "play-ahc-ws-standalone" % playWsStandaloneVersion,
    "com.typesafe.play"             % "shaded-asynchttpclient"  % playWsStandaloneVersion,
    "com.typesafe.play"             % "shaded-oauth"            % playWsStandaloneVersion,
    "com.github.ben-manes.caffeine" % "jcache"                  % caffeineVersion % Test,
  )
}
