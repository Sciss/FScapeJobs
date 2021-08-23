import de.sciss.fscape.FScapeJobs
import de.sciss.audiofile.{AudioFile, AudioFileSpec}

import java.io.File

object Test {
  def main(args: Array[String]): Unit = {
    val fsc = FScapeJobs()
    println("Connecting... ")
    fsc.connect() { success =>
      println(if (success) "success" else "failure")
      if (success) {
        println("Processing... ")
        val fIn   = File.createTempFile("tmp", ".aif")
        val SR    = 44100
        val af    = AudioFile.openWrite(fIn, AudioFileSpec(numChannels = 1, sampleRate = SR))
        try {
          val b0  = Array.tabulate[Double](SR) { i =>
            math.sin(i.toDouble * 2 * math.Pi * 1000.0 / SR)
          }
          af.write(Array(b0))

        } finally {
          af.close()
        }
        val fOut  = new File("hilbert.aif")

        val doc = FScapeJobs.Hilbert(in = fIn.getAbsolutePath, out = fOut.getAbsolutePath,
          gain = FScapeJobs.Gain.normalized, freq = 440.0 - 1000.0)

        fsc.process(
          name = "foo",
          doc  = doc,
          progress = { i =>
            println(s"Progress: $i")
          }
        ) { success =>
          println(if (success) "success" else "failure")
          sys.exit(if (success) 0 else 1)
        }

      } else {
        sys.exit(1)
      }
    }
  }
}
