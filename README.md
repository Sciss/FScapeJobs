# FScapeJobs

## statement

(C)opyright 2010&ndash;2014 Hanns Holger Rutz. This software is released under the [GNU Lesser General Public License](http://github.com/Sciss/FScapeJobs/blob/master/LICENSE) v2.1+.

FScapeJobs provides a simple OSC client to talk to the [FScape](http://sourceforge.net/projects/fscape/) audio signal processing toolbox, along with Scala configuration classes for most of its modules.

## building

Builds with sbt 0.13 against Scala 2.11, 2.10. Depends on [ScalaOSC](http://github.com/Sciss/ScalaOSC). Standard sbt targets are `clean`, `update`, `compile`, `package`, `doc`, `publish-local`.

## linking

Link to the following artifact:

    "de.sciss" %% "fscapejobs" % v

The current version `v` is `"1.5.0"`.
