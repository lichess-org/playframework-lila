// Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>

lazy val plugins = (project in file(".")).settings(
  scalaVersion := "2.12.17", // TODO: remove when upgraded to sbt 1.8.0 (maybe even 1.7.2), see https://github.com/sbt/sbt/pull/7021
)

enablePlugins(BuildInfoPlugin)

val mima              = "1.1.1"
val scalafmt          = "2.4.6"
val sbtTwirl: String  = sys.props.getOrElse("twirl.version", "1.6.1") // sync with documentation/project/plugins.sbt
val interplay: String = sys.props.getOrElse("interplay.version", "3.1.7")

buildInfoKeys := Seq[BuildInfoKey](
  "sbtTwirlVersion" -> sbtTwirl,
)

logLevel := Level.Warn

scalacOptions ++= Seq("-deprecation", "-language:_")

addSbtPlugin("com.typesafe.play" % "interplay"       % interplay)
addSbtPlugin("com.typesafe.play" % "sbt-twirl"       % sbtTwirl)
addSbtPlugin("com.typesafe"      % "sbt-mima-plugin" % mima)
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"    % scalafmt)
addSbtPlugin("com.github.sbt"    % "sbt-ci-release"  % "1.5.11")

addSbtPlugin("com.lightbend.akka" % "sbt-akka-version-check" % "0.1")

resolvers += Resolver.typesafeRepo("releases")
