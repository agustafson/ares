import sbt._

name := "ares"

val baseSettings = Seq(
  organization := "com.itv",
  scalaVersion := "2.11.8",
  scalafmtConfig in ThisBuild := Some(file(".scalafmt.conf"))
)

val scalacSettings = Seq(
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8", // 2 args
    "-feature",
    "-deprecation",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:experimental.macros",
    "-unchecked",
    "-Xlint",
    "-Yno-adapted-args",
    //"-Ywarn-dead-code",
    "-Ywarn-value-discard"
  ),
  scalacOptions in (Compile, doc) ++= Seq(
    "-groups",
    "-sourcepath",
    (baseDirectory in LocalRootProject).value.getAbsolutePath
  )
)
/*
libraryDependencies ++= {
  val fs2 = "0.9.1"
  Seq(
    "com.thangiee"               %% "freasy-monad"      % "0.4.1",
    "org.typelevel"              %% "cats"              % "0.7.2",
    "co.fs2"                     %% "fs2-core"          % fs2,
    "co.fs2"                     %% "fs2-io"            % fs2,
    "co.fs2"                     %% "fs2-cats"          % "0.1.0",
    "com.typesafe.scala-logging" %% "scala-logging"     % "3.5.0",
    "org.scalatest"              %% "scalatest"         % "3.0.0" % "test",
    "org.scalacheck"             %% "scalacheck"        % "1.13.4" % "test",
    "org.specs2"                 %% "specs2-core"       % "3.8.4" % "test",
    "org.specs2"                 %% "specs2-scalacheck" % "3.8.4" % "test"
  )
}
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
 */

lazy val core = project
  .settings(baseSettings)
  .settings(scalacSettings)
  .settings(
    Seq(
      resolvers += Resolver.jcenterRepo,
      libraryDependencies ++= {
        Seq(
          "org.typelevel"              %% "cats"              % "0.7.2",
          "com.thangiee"               %% "freasy-monad"      % "0.4.1",
          "com.typesafe.scala-logging" %% "scala-logging"     % "3.5.0",
          "org.scalacheck"             %% "scalacheck"        % "1.13.4" % "test",
          "org.specs2"                 %% "specs2-core"       % "3.8.4" % "test",
          "org.specs2"                 %% "specs2-scalacheck" % "3.8.4" % "test"
        )
      },
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    ))

lazy val macros = project
  .dependsOn(core)
  .settings(baseSettings)
  .settings(scalacSettings)
  .settings(
    Seq(
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    ))

lazy val fs2 = project
  .dependsOn(core, macros)
  .settings(baseSettings)
  .settings(scalacSettings)
  .settings(
    Seq(
      resolvers += Resolver.jcenterRepo,
      libraryDependencies ++= {
        val fs2 = "0.9.1"
        Seq(
          "org.typelevel"              %% "cats"              % "0.7.2",
          "co.fs2"                     %% "fs2-core"          % fs2,
          "co.fs2"                     %% "fs2-io"            % fs2,
          "co.fs2"                     %% "fs2-cats"          % "0.1.0",
          "com.typesafe.scala-logging" %% "scala-logging"     % "3.5.0",
          "org.scalacheck"             %% "scalacheck"        % "1.13.4" % "test",
          "org.specs2"                 %% "specs2-core"       % "3.8.4" % "test",
          "org.specs2"                 %% "specs2-scalacheck" % "3.8.4" % "test"
        )
      }
    ))
