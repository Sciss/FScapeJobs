lazy val commonSettings = Seq(
  name               := "FScapeJobs",
  version            := "1.6.0",
  organization       := "de.sciss",
  scalaVersion       := "2.13.6",
  crossScalaVersions := Seq("2.13.6", "2.12.14"),
  description        := "A library to launch digital signal processing jobs for FScape via OSC",
  homepage           := Some(url("https://github.com/Sciss/" + name.value)),
  licenses           := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
)

lazy val deps = new {
  val main = new {
    val akka      = "2.5.32"
    val audioFile = "2.3.3"
    val osc       = "1.3.1"
  }
}

lazy val root = project.in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    console / initialCommands :=
      """import de.sciss.fscape.FScapeJobs
        |import FScapeJobs._""".stripMargin,
    libraryDependencies ++= Seq(
      "de.sciss"          %% "scalaosc"     % deps.main.osc,
      "de.sciss"          %% "audiofile"    % deps.main.audioFile,
      "com.typesafe.akka" %% "akka-actor"   % deps.main.akka,
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-encoding", "utf8"),
    // ---- build info ----
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt) => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.fscape.jobs"
  )

// ---- publishing ----

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
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
)
