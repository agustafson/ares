organization := "com.itv"

name := "ares"

scalaVersion := "2.11.8"

crossScalaVersions := Seq(scalaVersion.value, "2.12.0-RC2")

scalacOptions ++= Seq(
  "-encoding", "UTF-8", // 2 args
  "-feature",
  "-deprecation",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:experimental.macros",
  "-unchecked",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-value-discard"
)

scalacOptions in (Compile, doc) ++= Seq(
  "-groups",
  "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath
)

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.8.0")

resolvers += Resolver.jcenterRepo

libraryDependencies ++= {
  val fs2 = "0.9.1"
  Seq(
    "redis.clients" % "jedis" % "2.8.1",
    "com.thangiee" %% "freasy-monad" % "0.4.1",
    "org.typelevel" %% "cats" % "0.7.2",
    "co.fs2" %% "fs2-core" % fs2,
    "co.fs2" %% "fs2-io" % fs2,
    "org.scalacheck" %% "scalacheck" % "1.13.3" % "test",
    "org.specs2"     %% "specs2-core"       % "3.8.4"  % "test",
    "org.specs2"     %% "specs2-scalacheck" % "3.8.4"  % "test"
  )
}
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
