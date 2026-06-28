// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

lazy val plugins = (project in file(".")).settings(
)

enablePlugins(BuildInfoPlugin)

val sbtTwirl: String = "1.6.1"

buildInfoKeys := Seq[BuildInfoKey](
  "sbtTwirlVersion" -> sbtTwirl,
)

logLevel := Level.Warn

scalacOptions ++= Seq("-deprecation", "-language:_")

addSbtPlugin("com.typesafe.play" % "sbt-twirl"    % sbtTwirl)
addSbtPlugin("org.scalameta"     % "sbt-scalafmt" % "2.6.1")


