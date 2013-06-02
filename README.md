# FScapeJobs

## statement

(C)opyright 2010&ndash;2013 Hanns Holger Rutz. This software is released under the [GNU General Public License](http://github.com/Sciss/FScapeJobs/blob/master/LICENSE).

FScapeJobs provides a simple OSC client to talk to the [FScape](http://sourceforge.net/projects/fscape/) audio signal processing toolbox, along with Scala configuration classes for most of its modules.

## building

Builds with sbt 0.12 against Scala 2.10. Depends on [ScalaOSC](http://github.com/Sciss/ScalaOSC). Standard sbt targets are `clean`, `update`, `compile`, `package`, `doc`, `publish-local`.

## linking

Link to the following artifact:

    "de.sciss" %% "fscapejobs" % v

The current version `v` is `"1.4.+"`.
