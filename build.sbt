
name := "balancer"

version := "1.0"

lazy val `balancer` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

resolvers += Resolver.bintrayRepo("cakesolutions", "maven")

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  jdbc,
  ehcache,
  ws,
  specs2 % Test,
  guice,
  "net.cakesolutions" %% "scala-kafka-client" % "0.11.0.0",
  "com.typesafe.akka" %% "akka-remote" % "2.5.1",
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.17"
)

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")
enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)

dockerfile in docker := {
  val appDir: File = stage.value
  val targetDir = "/app"

  new Dockerfile {
    expose(9000)
    from("java")
    entryPoint(s"$targetDir/bin/${executableScriptName.value}")
    copy(appDir, targetDir)
  }
}
      