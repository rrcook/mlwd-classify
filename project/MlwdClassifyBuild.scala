import sbt._
import sbt.Keys._

object MlwdClassifyBuild extends Build {

  lazy val mlwdClassify = Project(
    id = "mlwd-classify",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "mlwd classify",
      organization := "mlclass",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.9.2",
      resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
      resolvers += "OpenNLPRepository" at "http://opennlp.sourceforge.net/maven2",
      resolvers += "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/",
      libraryDependencies += "org.apache.lucene" % "lucene-snowball" % "3.0.3",
      libraryDependencies += "com.typesafe.akka" % "akka-actor" % "2.0.1",
      libraryDependencies += "com.jasonbaldridge" % "chalk" % "1.0"
    )
  )
}
