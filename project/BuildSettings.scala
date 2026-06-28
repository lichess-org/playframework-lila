/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */
import java.util.regex.Pattern
import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport._

import scala.sys.process.stringToProcess
import scala.util.control.NonFatal

object BuildSettings {

  val playVersion = "2.8.18-lila_3.23"

  val SourcesApplication = config("sources").hide

  /** These settings are used by all projects. */
  def playCommonSettings: Seq[Setting[?]] = Def.settings(
    organization := "com.typesafe.play",
    scalaVersion := "3.8.4",
    javacOptions ++= Seq("-encoding", "UTF-8", "--release", "21"),
    (Compile / doc / scalacOptions) := Seq("-no-java-comments"),
    (Test / fork) := true,
    (Test / parallelExecution) := false,
    (Test / test / testListeners) := Nil,
    (Test / javaOptions) ++= Seq("-XX:MaxMetaspaceSize=384m", "-Xmx512m", "-Xms128m"),
    testOptions ++= Seq(
      Tests.Argument(TestFrameworks.Specs2, "showtimes"),
      Tests.Argument(TestFrameworks.JUnit, "-v")
    ),
    version := playVersion
  )

  /** A project that is in the Play runtime. */
  def PlayCrossBuiltProject(name: String, dir: String): Project = {
    Project(name, file(dir))
      .settings(playCommonSettings)
  }

}
