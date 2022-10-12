// Copyright (C) Lightbend Inc. <https://www.lightbend.com>

lazy val plugins = (project in file(".")).settings(
  scalaVersion := "2.12.17", // TODO: remove when upgraded to sbt 1.8.0 (maybe even 1.7.2), see https://github.com/sbt/sbt/pull/7021
)

enablePlugins(BuildInfoPlugin)

// when updating sbtNativePackager version, be sure to also update the documentation links in
// documentation/manual/working/commonGuide/production/Deploying.md
val sbtNativePackager = "1.9.11"
val mima              = "1.1.0"
val scalafmt          = "2.4.6"
val sbtTwirl: String  = sys.props.getOrElse("twirl.version", "1.6.0-M7") // sync with documentation/project/plugins.sbt
val interplay: String = sys.props.getOrElse("interplay.version", "3.1.0-RC5")

buildInfoKeys := Seq[BuildInfoKey](
  "sbtNativePackagerVersion" -> sbtNativePackager,
  "sbtTwirlVersion"          -> sbtTwirl,
)

logLevel := Level.Warn

scalacOptions ++= Seq("-deprecation", "-language:_")

addSbtPlugin("com.typesafe.play" % "interplay"       % interplay)
addSbtPlugin("com.typesafe.play" % "sbt-twirl"       % sbtTwirl)
addSbtPlugin("com.typesafe"      % "sbt-mima-plugin" % mima)
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"    % scalafmt)
addSbtPlugin("com.github.sbt"    % "sbt-ci-release"  % "1.5.10")

addSbtPlugin("com.lightbend.akka" % "sbt-akka-version-check" % "0.1")

resolvers += Resolver.typesafeRepo("releases")
