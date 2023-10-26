// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

import BuildSettings._
import Dependencies._
import Generators._
import interplay.PlayBuildBase.autoImport._
import sbt.Keys.parallelExecution
import sbt._
import sbt.io.Path._
import org.scalafmt.sbt.ScalafmtPlugin

scalacOptions ++= Seq(
  "nowarn", // migration
  "-Xcheck-macros",
  "-source:future-migration",
  "-rewrite",
  "-release:21",
)

lazy val RoutesCompilerProject = PlayDevelopmentProject("Routes-Compiler", "dev-mode/routes-compiler")
  .enablePlugins(SbtTwirl)
  .settings(
    libraryDependencies ++= routesCompilerDependencies(scalaVersion.value),
    TwirlKeys.templateFormats := Map("twirl" -> "play.routes.compiler.ScalaFormat")
  )

lazy val StreamsProject = PlayCrossBuiltProject("Play-Streams", "core/play-streams")
  .settings(libraryDependencies ++= streamsDependencies)

lazy val PlayExceptionsProject = PlayNonCrossBuiltProject("Play-Exceptions", "core/play-exceptions")

lazy val PlayProject = PlayCrossBuiltProject("Play", "core/play")
  .enablePlugins(SbtTwirl)
  .settings(
    libraryDependencies ++= runtime(scalaVersion.value) ++ scalacheckDependencies ++ cookieEncodingDependencies :+
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

lazy val PlayServerProject = PlayCrossBuiltProject("Play-Server", "transport/server/play-server")
  .settings(libraryDependencies ++= playServerDependencies)
  .dependsOn(
    PlayProject,
  )

lazy val PlayNettyServerProject = PlayCrossBuiltProject("Play-Netty-Server", "transport/server/play-netty-server")
  .settings(libraryDependencies ++= netty)
  .dependsOn(PlayServerProject)

lazy val PlayLogback = PlayCrossBuiltProject("Play-Logback", "core/play-logback")
  .settings(
    libraryDependencies += logback,
    parallelExecution in Test := false,
    // quieten deprecation warnings in tests
    scalacOptions in Test := (scalacOptions in Test).value.diff(Seq("-deprecation"))
  )
  .dependsOn(PlayProject)

lazy val PlayConfiguration = PlayCrossBuiltProject("Play-Configuration", "core/play-configuration")
  .settings(
    libraryDependencies ++= Seq(typesafeConfig, slf4jApi) ++ specs2Deps.map(_ % Test),
    (Test / parallelExecution) := false,
    mimaPreviousArtifacts      := Set.empty,
    // quieten deprecation warnings in tests
    (scalacOptions in Test) := (scalacOptions in Test).value.diff(Seq("-deprecation"))
  )
  .dependsOn(PlayExceptionsProject)

// These projects are aggregate by the root project and every
// task (compile, test, publish, etc) executed for the root
// project will also be executed for them:
// https://www.scala-sbt.org/1.x/docs/Multi-Project.html#Aggregation
//
// Keep in mind that specific configurations (like skip in publish) will be respected.
lazy val userProjects = Seq[ProjectReference](
  PlayProject,
  RoutesCompilerProject,
  PlayNettyServerProject,
  PlayServerProject,
  PlayLogback,
  PlayConfiguration,
  PlayExceptionsProject,
  StreamsProject
)
lazy val nonUserProjects = Seq[ProjectReference](
  // SbtRoutesCompilerProject,
  // SbtPluginProject,
)

lazy val PlayFramework = Project("Play-Framework", file("."))
  .enablePlugins(PlayRootProject)
  .settings(
    playCommonSettings,
    scalaVersion                    := (scalaVersion in PlayProject).value,
    crossScalaVersions              := Nil,
    (playBuildRepoName in ThisBuild) := "playframework",
    (concurrentRestrictions in Global) += Tags.limit(Tags.Test, 1),
    libraryDependencies ++= runtime(scalaVersion.value),
    mimaReportBinaryIssues := (()),
    commands += Commands.quickPublish,
    publish / skip := true,
  )
  .aggregate((userProjects ++ nonUserProjects): _*)
