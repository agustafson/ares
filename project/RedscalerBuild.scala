import sbt._

object RedscalerBuild {

  object Dependencies {
    private val fs2Version  = "0.9.2"
    private val catsVersion = "3.8.6"

    lazy val cats             = "org.typelevel"              %% "cats"              % "0.9.0"
    lazy val fs2Core          = "co.fs2"                     %% "fs2-core"          % fs2Version
    lazy val fs2Io            = "co.fs2"                     %% "fs2-io"            % fs2Version
    lazy val fs2Cats          = "co.fs2"                     %% "fs2-cats"          % "0.2.0"
    lazy val freasyMonad      = "com.github.thangiee"        %% "freasy-monad"      % "0.6.0"
    lazy val scalaLogging     = "com.typesafe.scala-logging" %% "scala-logging"     % "3.5.0"
    lazy val logbackClassic   = "ch.qos.logback"             % "logback-classic"    % "1.1.7"
    lazy val scalaPool        = "io.github.andrebeat"        %% "scala-pool"        % "0.4.0"
    lazy val macroParadise    = "org.scalameta"              % "paradise"           % "3.0.0-M8"
    lazy val scalacheck       = "org.scalacheck"             %% "scalacheck"        % "1.13.4"
    lazy val specs2Core       = "org.specs2"                 %% "specs2-core"       % catsVersion
    lazy val specs2Scalacheck = "org.specs2"                 %% "specs2-scalacheck" % catsVersion
  }

}
