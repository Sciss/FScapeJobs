# FScapeJobs

[![Build Status](https://github.com/Sciss/FScapeJobs/workflows/Scala%20CI/badge.svg?branch=main)](https://github.com/Sciss/FScapeJobs/actions?query=workflow%3A%22Scala+CI%22)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sciss/fscapejobs_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sciss/fscapejobs_2.13)

## statement

(C)opyright 2010&ndash;2021 Hanns Holger Rutz. This software is released under the 
[GNU Lesser General Public License](http://github.com/Sciss/FScapeJobs/blob/master/LICENSE) v2.1+.

FScapeJobs provides a simple OSC client to talk to the [FScape (classic)](http://sourceforge.net/projects/fscape/) audio signal 
processing toolbox, along with Scala configuration classes for most of its modules.
__Note:__ This project is mostly abandoned, since Scala 2.12 internals were changed from scala-actors to akka,
but this has not been much tested!

## building

Builds with sbt against Scala 2.13, 2.12. The last version to support Scala 2.11 was v1.5.0.
Standard sbt targets are `clean`, `update`, `compile`, `package`, `doc`, `publishLocal`.

A simple test can be run with the `Test` class; it should produce a sound file `hilbert.aif` containing
a 440 Hz tone lasting one second. You must have started FScape classic beforehand, enabling its OSC interface
at the standard TCP port 18003.

## linking

Link to the following artifact:

    "de.sciss" %% "fscapejobs" % v

The current version `v` is `"1.6.0"`.
