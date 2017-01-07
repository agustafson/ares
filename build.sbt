import sbt._
import RedscalerBuild._

organization := "io.redscaler"
name := "redscaler"
scalaVersion := "2.12.1"
crossScalaVersions := Seq("2.11.8", scalaVersion.value)

val baseSettings = Seq(
  organization := "io.redscaler",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.8", scalaVersion.value),
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
  .settings(Seq(
    resolvers += Resolver.jcenterRepo,
    libraryDependencies ++= {
      Seq(
        Dependencies.cats,
        Dependencies.freasyMonad,
        Dependencies.scalaLogging,
        Dependencies.scalacheck       % "test",
        Dependencies.specs2Core       % "test",
        Dependencies.specs2Scalacheck % "test"
      )
    },
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  ))

lazy val fs2 = project
  .dependsOn(core)
  .settings(baseSettings)
  .settings(scalacSettings)
  .settings(Seq(
    resolvers += Resolver.jcenterRepo,
    resolvers += Resolver.sonatypeRepo("releases"),
    libraryDependencies ++= {
      Seq(
        Dependencies.cats,
        Dependencies.fs2Core,
        Dependencies.fs2Io,
        Dependencies.fs2Cats,
        Dependencies.scalaLogging,
        Dependencies.logbackClassic   % "test",
        Dependencies.scalacheck       % "test",
        Dependencies.specs2Core       % "test",
        Dependencies.specs2Scalacheck % "test"
      )
    },
    addCompilerPlugin("org.scalamacros" % "paradise"       % "2.1.0" cross CrossVersion.full),
    addCompilerPlugin("org.spire-math"  % "kind-projector" % "0.9.3" cross CrossVersion.binary)
  ))
