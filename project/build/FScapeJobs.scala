import sbt._

class FScapeJobsProject( info: ProjectInfo ) extends DefaultProject( info ) {
   val scalaOSC         = "de.sciss" %% "scalaosc" % "0.20"
   val scalaAudioFile   = "de.sciss" %% "scalaaudiofile" % "0.14"
}
