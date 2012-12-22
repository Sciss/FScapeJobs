## FScapeJobs

### statement

(C)opyright 2010&ndash;2012 Hanns Holger Rutz. This software is released under the [GNU General Public License](http://github.com/Sciss/FScapeJobs/blob/master/licenses/FScapeJobs-License.txt).

FScapeJobs provides a simple OSC client to talk to the [FScape](http://sourceforge.net/projects/fscape/) audio signal processing toolbox, along with Scala configuration classes for most of its modules.

### requirements

Builds with sbt 0.12 against Scala 2.10 (default) and 2.9.2. Depends on [ScalaOSC](http://github.com/Sciss/ScalaOSC). Standard sbt targets are `clean`, `update`, `compile`, `package`, `doc`, `publish-local`.

To depend on FScapeJobs in your project:

    "de.sciss" %% "fscapejobs" % "1.2.+"

### creating an IntelliJ IDEA project

To develop the sources, if you haven't globally installed the sbt-idea plugin yet, create the following contents in `~/.sbt/plugins/build.sbt`:

    addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.1.0")

Then to create the IDEA project, run `sbt gen-idea`.
