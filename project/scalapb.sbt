resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.7.0-SNAPSHOT")
 addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.1.0")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.8")
libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.0-pre4"