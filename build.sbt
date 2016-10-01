name         := "FScapeJobs"
version      := "0.17"
organization := "de.sciss"
scalaVersion := "2.11.8"
description  := "A library to launch FScape processing jobs via OSC"
homepage     := Some(url("https://github.com/Sciss/FScapeJobs"))
licenses     := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

libraryDependencies ++= Seq(
   "de.sciss"       %% "scalaosc"       % "0.33",
   "de.sciss"       %% "scalaaudiofile" % "0.20",
   "org.scala-lang" %  "scala-actors"   % scalaVersion.value
)

scalacOptions += "-deprecation"

// ---- publishing ----

publishMavenStyle := true

publishTo :=
   Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )

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
