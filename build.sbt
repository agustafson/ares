import sbt._

name := "redscaler"

val baseSettings = Seq(
  organization := "io.redscaler",
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

lazy val core = project
  .settings(baseSettings)
  .settings(scalacSettings)
  .settings(
    Seq(
      resolvers += Resolver.jcenterRepo,
      libraryDependencies ++= {
        Seq(
          "org.typelevel"              %% "cats"              % "0.8.1",
          "com.github.thangiee"        %% "freasy-monad"      % "0.5.0",
          "com.typesafe.scala-logging" %% "scala-logging"     % "3.5.0",
          "org.scalacheck"             %% "scalacheck"        % "1.13.4" % "test",
          "org.specs2"                 %% "specs2-core"       % "3.8.4" % "test",
          "org.specs2"                 %% "specs2-scalacheck" % "3.8.4" % "test"
        )
      },
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
    ))

lazy val fs2 = project
  .dependsOn(core)
  .settings(baseSettings)
  .settings(scalacSettings)
  .settings(
    Seq(
      resolvers += Resolver.jcenterRepo,
      resolvers += Resolver.sonatypeRepo("releases"),
      libraryDependencies ++= {
        val fs2 = "0.9.2"
        Seq(
          "org.typelevel"              %% "cats"              % "0.8.1",
          "co.fs2"                     %% "fs2-core"          % fs2,
          "co.fs2"                     %% "fs2-io"            % fs2,
          "co.fs2"                     %% "fs2-cats"          % "0.2.0",
          "com.typesafe.scala-logging" %% "scala-logging"     % "3.5.0",
          "ch.qos.logback"             % "logback-classic"    % "1.1.7" % "test",
          "org.scalacheck"             %% "scalacheck"        % "1.13.4" % "test",
          "org.specs2"                 %% "specs2-core"       % "3.8.4" % "test",
          "org.specs2"                 %% "specs2-scalacheck" % "3.8.4" % "test"
        )
      },
      addCompilerPlugin("org.scalamacros" % "paradise"       % "2.1.0" cross CrossVersion.full),
      addCompilerPlugin("org.spire-math"  % "kind-projector" % "0.9.3" cross CrossVersion.binary)
    ))
