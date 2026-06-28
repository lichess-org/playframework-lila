// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

import BuildSettings._
import Dependencies._
import Generators._
import sbt.Keys.parallelExecution
import sbt._
import sbt.io.Path._
import org.scalafmt.sbt.ScalafmtPlugin

ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "utf-8",
  "-release:21",
  "-rewrite",
  // "-Wunused:all",
  // "-Werror",
  "-deprecation"
)
ThisBuild / publishTo := Option(Resolver.file("file", new File(sys.props.getOrElse("publishTo", ""))))

lazy val StreamsProject = Project("Play-Streams", file("core/play-streams"))
  .settings(playCommonSettings)
  .settings(libraryDependencies ++= streamsDependencies)

lazy val PlayExceptionsProject = Project("Play-Exceptions", file("core/play-exceptions"))
  .settings(playCommonSettings)
  .settings(
    autoScalaLibrary := false,
    crossPaths := false
  )

lazy val PlayProject = Project("Play", file("core/play"))
  .enablePlugins(SbtTwirl)
  .settings(playCommonSettings)
  .settings(
    libraryDependencies ++= runtime(
      scalaVersion.value
    ) ++ scalacheckDependencies ++ cookieEncodingDependencies :+
      jimfs % Test,
    (sourceGenerators in Compile) += Def
      .task(
        PlayVersion(
          version.value,
          scalaVersion.value,
          sbtVersion.value,
          Dependencies.akkaVersion,
          Dependencies.akkaHttpVersion,
          (sourceManaged in Compile).value
        )
      )
      .taskValue
  )
  .dependsOn(PlayExceptionsProject, PlayConfiguration, StreamsProject)

lazy val PlayServerProject = Project("Play-Server", file("transport/server/play-server"))
  .settings(playCommonSettings)
  .settings(libraryDependencies ++= playServerDependencies)
  .dependsOn(PlayProject)

lazy val PlayNettyServerProject = Project("Play-Netty-Server", file("transport/server/play-netty-server"))
  .settings(playCommonSettings)
  .settings(libraryDependencies ++= netty)
  .dependsOn(PlayServerProject)

lazy val PlayLogback = Project("Play-Logback", file("core/play-logback"))
  .settings(playCommonSettings)
  .settings(
    libraryDependencies += logback,
    parallelExecution in Test := false,
    // quieten deprecation warnings in tests
    scalacOptions in Test := (scalacOptions in Test).value.diff(Seq("-deprecation"))
  )
  .dependsOn(PlayProject)

lazy val PlayConfiguration = Project("Play-Configuration", file("core/play-configuration"))
  .settings(playCommonSettings)
  .settings(
    libraryDependencies ++= Seq(typesafeConfig, slf4jApi) ++ specs2Deps.map(_ % Test),
    (Test / parallelExecution) := false,
    // quieten deprecation warnings in tests
    (scalacOptions in Test) := (scalacOptions in Test).value.diff(Seq("-deprecation"))
  )
  .dependsOn(PlayExceptionsProject)

lazy val PlayFramework = Project("Play-Framework", file("."))
  .settings(publish / skip := true)
  .aggregate(
    PlayProject,
    PlayNettyServerProject,
    PlayServerProject,
    PlayLogback,
    PlayConfiguration,
    PlayExceptionsProject,
    StreamsProject
  )
