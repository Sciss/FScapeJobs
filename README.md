## FScapeJobs

### statement

(C)opyright 2010--2011 Hanns Holger Rutz. This software is released under the [GNU General Public License](http://github.com/Sciss/FScapeJobs/blob/master/licenses/FScapeJobs-License.txt).

FScapeJobs provides a simple OSC client to talk to the [FScape](http://sourceforge.net/projects/fscape/) audio signal processing toolbox, along with Scala configuration classes for most of its modules.

### requirements

Builds with xsbt (sbt 0.11) against Scala 2.9.1. Depends on [ScalaOSC](http://github.com/Sciss/ScalaOSC). Standard sbt targets are `clean`, `update`, `compile`, `package`, `doc`, `publish-local`.

### creating an IntelliJ IDEA project

If you haven't globally installed the sbt-idea plugin yet, create the following contents in `~/.sbt/plugins/build.sbt`:

    resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"
    
    addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "0.11.0")

Then to create the IDEA project, run the following two commands from the xsbt shell:

    > set ideaProjectName := "FScapeJobs"
    > gen-idea
