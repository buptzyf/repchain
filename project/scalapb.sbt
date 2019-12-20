resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.7.0-SNAPSHOT")
 addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.25")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.0-M2"