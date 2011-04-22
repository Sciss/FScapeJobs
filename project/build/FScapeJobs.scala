import sbt._

class FScapeJobsProject( info: ProjectInfo ) extends DefaultProject( info ) {
   val dep1 = "de.sciss" %% "scalaosc" % "0.22"
   val dep2 = "de.sciss" %% "scalaaudiofile" % "0.16"
}
