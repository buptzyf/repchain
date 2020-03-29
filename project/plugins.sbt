import sbt._
import Defaults._

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.0.9")
resolvers += Resolver.url("bintray-sbt-plugins", url("http://dl.bintray.com/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers ++= Seq(
  Resolver.bintrayRepo("epfl-lara", "princess"),
  Resolver.bintrayIvyRepo("epfl-lara", "sbt-plugins"),
  ("uuverifiers" at "http://logicrunch.research.it.uu.se/maven").withAllowInsecureProtocol(true),
)

addSbtPlugin("ch.epfl.lara" % "sbt-stainless" % "0.7.0")