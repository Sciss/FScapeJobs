name               := "FScapeJobs"

version            := "1.4.1-SNAPSHOT"

organization       := "de.sciss"

scalaVersion       := "2.11.0-RC3"

crossScalaVersions := Seq("2.11.0-RC3", "2.10.4")

description        := "A library to launch digital signal processing jobs for FScape via OSC"

homepage           := Some(url("https://github.com/Sciss/" + name.value))

licenses           := Seq("LGPL v3+" -> url("http://www.gnu.org/licenses/lgpl-3.0.txt"))

lazy val oscVersion       = "1.1.+"

lazy val audioFileVersion = "1.4.+"

initialCommands in console :=
  """import de.sciss.fscape.FScapeJobs
    |import FScapeJobs._""".stripMargin

libraryDependencies ++= Seq(
  "de.sciss"       %% "scalaosc"       % oscVersion,
  "de.sciss"       %% "scalaaudiofile" % audioFileVersion,
  "org.scala-lang" %  "scala-actors"   % scalaVersion.value
)

// retrieveManaged := true

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

// ---- build info ----

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
  BuildInfoKey.map(homepage) { case (k, opt) => k -> opt.get },
  BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
)

buildInfoPackage := "de.sciss.fscape.jobs"

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (version.value endsWith "-SNAPSHOT")
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
}

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("fscape", "audio", "dsp")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)

