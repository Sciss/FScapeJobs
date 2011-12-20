name := "fscapejobs"

version := "0.17"

organization := "de.sciss"

scalaVersion := "2.9.1"

// crossScalaVersions := Seq("2.9.1", "2.9.0", "2.8.1")

libraryDependencies ++= Seq(
   "de.sciss" %% "scalaosc" % "0.33",
   "de.sciss" %% "scalaaudiofile" % "0.20"
)

retrieveManaged := true

scalacOptions += "-deprecation"

