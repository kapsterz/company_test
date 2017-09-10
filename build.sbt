name := "lotusflare_test"

version := "1.0"

lazy val `lotusflare_test` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  jdbc,
  ehcache,
  ws,
  specs2 % Test,
  guice,
  "com.typesafe.akka" %% "akka-remote" % "2.5.1",
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.17")

unmanagedResourceDirectories in Test <+= baseDirectory(_ / "target/web/public/test")
enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)
enablePlugins(DockerPlugin)

dockerfile in docker := {
  val appDir: File = stage.value
  val targetDir = "/app"

  new Dockerfile {
    from("java")
    entryPoint(s"$targetDir/bin/${executableScriptName.value}")
    copy(appDir, targetDir)
  }
}