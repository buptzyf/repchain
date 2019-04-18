name := """RepChain"""

version := "0.9"

scalaVersion := "2.12.8"

lazy val akkaVersion = "2.5.22"
val akkaHttpVersion   = "10.1.8"



libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion
  )

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
libraryDependencies += "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion
libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value

libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

libraryDependencies += "com.trueaccord.scalapb" %% "scalapb-json4s" % "0.3.3"

libraryDependencies += "org.iq80.leveldb" % "leveldb" % "0.11"
libraryDependencies += "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"

libraryDependencies += "org.mapdb" % "mapdb" % "3.0.7"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.7"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.7" % "test"

libraryDependencies += "org.javadelight" % "delight-nashorn-sandbox" % "0.1.22"

libraryDependencies += "io.spray" %%  "spray-json" % "1.3.5"

libraryDependencies += "com.gilt" %% "gfc-timeuuid" % "0.0.8"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

libraryDependencies += "org.codehaus.janino" % "janino" % "3.0.12"

libraryDependencies += "org.bouncycastle" % "bcprov-jdk15on" % "1.61"

libraryDependencies ++= Seq(
  "io.swagger" % "swagger-jaxrs" % "1.5.22",
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.0.2",
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "de.heikoseeberger" % "akka-http-json4s_2.11" % "1.25.2",
  "org.json4s" %% "json4s-native" % "3.6.5",
  "org.json4s" %% "json4s-jackson" % "3.6.5",

  "ch.megard" %% "akka-http-cors" % "0.4.0",
  "com.twitter" % "chill-bijection_2.11" % "0.9.3"
)	

javacOptions ++= Seq("-encoding", "UTF-8")

libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"

mainClass in (Compile, packageBin) := Some("rep.app.Repchain")