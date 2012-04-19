name := "FScapeJobs"

version := "0.17"

organization := "de.sciss"

scalaVersion := "2.9.2"

description := "A library to launch FScape processing jobs via OSC"

homepage := Some( url( "https://github.com/Sciss/FScapeJobs" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

// crossScalaVersions := Seq("2.9.1", "2.9.0", "2.8.1")

libraryDependencies ++= Seq(
   "de.sciss" %% "scalaosc" % "0.33",
   "de.sciss" %% "scalaaudiofile" % "0.20"
)

retrieveManaged := true

scalacOptions += "-deprecation"

// ---- publishing ----

publishMavenStyle := true

publishTo <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/FScapeJobs.git</url>
  <connection>scm:git:git@github.com:Sciss/FScapeJobs.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

// ---- ls.implicit.ly ----

seq( lsSettings :_* )

(LsKeys.tags in LsKeys.lsync) := Seq( "fscape", "audio", "dsp" )

(LsKeys.ghUser in LsKeys.lsync) := Some( "Sciss" )

(LsKeys.ghRepo in LsKeys.lsync) := Some( "FScapeJobs" )

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))
