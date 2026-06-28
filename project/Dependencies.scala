/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt._
import Keys._

import buildinfo.BuildInfo

object Dependencies {
  val akkaVersion: String = sys.props.getOrElse("akka.version", "2.6.21")
  val akkaHttpVersion = sys.props.getOrElse("akka.http.version", "10.2.10")

  val logback = "ch.qos.logback" % "logback-classic" % "1.5.35"

  val specs2Version = "4.20.0"
  val specs2CoreDeps = Seq(
    "specs2-core",
    "specs2-junit"
  ).map("org.specs2" %% _ % specs2Version)
  val specs2Deps = specs2CoreDeps

  val specsMatcherExtra = "org.specs2" %% "specs2-matcher-extra" % specs2Version

  val scalacheckDependencies = Seq(
    "org.specs2" %% "specs2-scalacheck" % specs2Version % Test,
    "org.scalacheck" %% "scalacheck" % "1.17.0" % Test
  )

  val playJson = "org.playframework" %% "play-json" % "3.0.4"

  val slf4jVersion = "2.0.18"
  val slf4j = Seq("slf4j-api", "jul-to-slf4j", "jcl-over-slf4j").map("org.slf4j" % _ % slf4jVersion)
  val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion
  val slf4jSimple = "org.slf4j" % "slf4j-simple" % slf4jVersion

  val guava = "com.google.guava" % "guava" % "32.1.3-jre"
  val findBugs = "com.google.code.findbugs" % "jsr305" % "3.0.2" // Needed by guava
  val mockitoAll = "org.mockito" % "mockito-core" % "4.11.0"
  val javaxInject = "javax.inject" % "javax.inject" % "1"

  def scalaParserCombinators(scalaVersion: String) =
    Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0")

  val junitInterface = "com.github.sbt" % "junit-interface" % "0.13.3"
  val junit = "junit" % "junit" % "4.13.2"

  def runtime(scalaVersion: String) =
    slf4j ++
      Seq("akka-actor", "akka-actor-typed", "akka-slf4j")
        .map("com.typesafe.akka" %% _ % akkaVersion) ++
      Seq("akka-testkit", "akka-actor-testkit-typed")
        .map("com.typesafe.akka" %% _ % akkaVersion % Test) ++
      Seq(
        playJson,
        guava,
        javaxInject
      ) ++ scalaParserCombinators(scalaVersion) ++ specs2Deps.map(_ % Test)

  val nettyVersion = "4.2.2.Final"

  val netty = Seq(
    "com.typesafe.netty" % "netty-reactive-streams-http" % "2.0.14",
    ("io.netty" % "netty-transport-native-epoll" % nettyVersion).classifier("linux-x86_64")
  ) ++ specs2Deps.map(_ % Test)

  val akkaHttp = "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion

  val akkaHttp2Support = "com.typesafe.akka" %% "akka-http2-support" % akkaHttpVersion

  val cookieEncodingDependencies = slf4j

  val jimfs = "com.google.jimfs" % "jimfs" % "1.2"

  val typesafeConfig = "com.typesafe" % "config" % "1.4.2"

  val streamsDependencies = Seq(
    "org.reactivestreams" % "reactive-streams" % "1.0.4",
    "com.typesafe.akka" %% "akka-stream" % akkaVersion
  ) ++ specs2CoreDeps.map(_ % Test)

  val playServerDependencies = specs2Deps.map(_ % Test) ++ Seq(
    guava % Test,
    logback % Test
  )

}
