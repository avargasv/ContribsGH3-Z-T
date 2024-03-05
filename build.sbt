name := "ContribsGH3-Z-T"

version := "0.1"

organization := "com.avargasv"

scalaVersion := "2.13.10"

scalacOptions ++= Seq("-Ymacro-annotations", "-deprecation", "-unchecked")

val zioVersion = "2.0.10"

libraryDependencies ++= Seq(
  "dev.zio"           %% "zio"                % zioVersion,
  "dev.zio"           %% "zio-http"           % "3.0.0-RC4",
  "dev.zio"           %% "zio-json"           % "0.5.0",
  "dev.zio"           %% "zio-macros"         % zioVersion,
  "com.github.kstyrc" %  "embedded-redis"     % "0.6",
  "redis.clients"     %  "jedis"              % "3.2.0",
  "dev.zio"           %% "zio-test"           % zioVersion % Test,
  "dev.zio"           %% "zio-test-sbt"       % zioVersion % Test,
  "dev.zio"           %% "zio-test-magnolia"  % zioVersion % Test,
  "org.slf4j"         %  "slf4j-simple"       % "2.0.0"    % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
