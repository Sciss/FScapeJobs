name := "FScapeJobs"

version := "1.0.0"

organization := "de.sciss"

scalaVersion := "2.9.2"

description := "A library to launch digital signal processing jobs for FScape via OSC"

homepage := Some( url( "https://github.com/Sciss/FScapeJobs" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

// crossScalaVersions := Seq("2.9.1", "2.9.0", "2.8.1")

libraryDependencies ++= Seq(
   "de.sciss" %% "scalaosc" % "1.0.+",
   "de.sciss" %% "scalaaudiofile" % "1.0.+"
)

libraryDependencies <++= scalaVersion { sv =>
   sv match {
      case "2.9.2" => Seq.empty
      case _ => Seq( "org.scala-lang" % "scala-actors" % sv )
   }
}

retrieveManaged := true

scalacOptions += "-deprecation"

// ---- build info ----

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq( name, organization, version, scalaVersion, description,
   BuildInfoKey.map( homepage ) { case (k, opt) => k -> opt.get },
   BuildInfoKey.map( licenses ) { case (_, Seq( (lic, _) )) => "license" -> lic }
)

buildInfoPackage := "de.sciss.fscape.jobs"

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
