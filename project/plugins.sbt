logLevel := Level.Warn

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.3")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.4.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0")