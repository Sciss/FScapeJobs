/*
 *  FScapeJobs.scala
 *  (FScapeJobs)
 *
 *  Copyright (c) 2010-2012 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.fscape

import de.sciss.synth.io.{AudioFileType, SampleFormat, AudioFileSpec}
import java.util.Properties
import java.io.{IOException, FileOutputStream, File}
import java.net.InetSocketAddress
import collection.mutable.{Queue => MQueue}
import collection.immutable.{IntMap, IndexedSeq => IIdxSeq}
import de.sciss.osc.{Dump, UDP, Transport, Message, TCP, Client}
import scala.actors.{TIMEOUT, DaemonActor}

object FScapeJobs {
  final val name          = "FScapeJobs"

  final val DEFAULT_PORT  = 0x4653

  /**
   * Creates a new FScape job-server. Note that currently, since the server will create
   * temporary files, FScape must run on the same machine as the client. Since jobs
   * are processed sequentially, you may still want to run several instances of FScape
   * (each with their dedicated OSC port) to use all available processors.
   *
   * @param   transport   the OSC transport to use
   * @param   addr        the OSC socket to connect to
   * @param   numThreads  the maximum number of processes carried out in parallel on the server
   * @return  the new job-server ready to receive job requests. It will initially try
   *          to connect to the OSC socket of FScape and starts processing the job queue once
   *          the connection has succeeded. 
   */
  def apply(transport: Transport.Net = TCP, addr: InetSocketAddress = new InetSocketAddress("127.0.0.1", DEFAULT_PORT),
            numThreads: Int = 1) = {
    require(numThreads > 0 && numThreads < 256) // 8 bit client mask currently
    new FScapeJobs(transport, addr, numThreads)
  }

  object Gain {
    val immediate = Gain("0.0dB", normalized = false)
    val normalized = Gain("-0.2dB", normalized = true)
  }

  object OutputSpec {
    val aiffFloat = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Float, 1, 44100.0)
    // numCh, sr not used
    val aiffInt = AudioFileSpec(AudioFileType.AIFF, SampleFormat.Int24, 1, 44100.0)
  }

  case class Gain(value: String = "0.0dB", normalized: Boolean = false)

  trait Doc {
    def write(p: Properties): Unit
    def className: String
  }

  private object Param {
    final val NONE        = 0x0000
    final val AMP         = 0x0001
    final val TIME        = 0x0002
    final val FREQ        = 0x0003
    final val PHASE       = 0x0004

    final val ABSUNIT     = 0x0000  // ms, Hz, ...
    final val ABSPERCENT  = 0x0010  // %
    final val RELUNIT     = 0x0020  // +/- ms, +/- Hz, ...
    final val RELPERCENT  = 0x0030  // +/- %

    final val BEATS       = 0x0100
    final val SEMITONES   = 0x0200
    final val DECIBEL     = 0x0300

    final val FACTOR            = NONE | ABSPERCENT
    final val ABS_AMP           = AMP | ABSUNIT
    final val FACTOR_AMP        = AMP | ABSPERCENT
    final val DECIBEL_AMP       = AMP | ABSPERCENT | DECIBEL
    final val OFFSET_AMP        = AMP | RELPERCENT
    final val ABS_MS            = TIME | ABSUNIT
    final val ABS_BEATS         = TIME | ABSUNIT | BEATS
    final val FACTOR_TIME       = TIME | ABSPERCENT
    final val OFFSET_MS         = TIME | RELUNIT
    final val OFFSET_BEATS      = TIME | RELUNIT | BEATS
    final val OFFSET_TIME       = TIME | RELPERCENT
    final val ABS_HZ            = FREQ | ABSUNIT
    final val FACTOR_FREQ       = FREQ | ABSPERCENT
    final val OFFSET_HZ         = FREQ | RELUNIT
    final val OFFSET_SEMITONES  = FREQ | RELUNIT | SEMITONES
    final val OFFSET_FREQ       = FREQ | RELPERCENT
  }

  private case class Param(value: Double, unit: Int) {
    override def toString = value.toString + "," + unit.toString
  }

  // ------------- actual processes -------------

   case class BinaryOp( in1: String, imagIn1: Option[ String ] = None,
                        in2: String, imagIn2: Option[ String ] = None, out: String, imagOut: Option[ String ] = None,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      offset1: String = "0.0", length1: String = "1.0",
      offset2: String = "0.0", length2: String = "1.0",
      op: String = "+",
      drive1: String = "0.0dB", rectify1: Boolean = false, invert1: Boolean = false,
      drive2: String = "0.0dB", rectify2: Boolean = false, invert2: Boolean = false,
      dryMix: String = "0.0", dryInvert: Boolean = false, wetMix: String = "1.0" )
   extends Doc {
      def className = "BinaryOp"

      def write( p: Properties ) {
         p.setProperty( "ReInFile1", in1 )
         p.setProperty( "ReInFile2", in2 )
         imagIn1.foreach( p.setProperty( "ImInFile1", _ ))
         imagIn2.foreach( p.setProperty( "ImInFile2", _ ))
         p.setProperty( "HasImInput1", imagIn1.isDefined.toString )
         p.setProperty( "HasImInput2", imagIn2.isDefined.toString )
         p.setProperty( "ReOutFile", out )
         imagOut.foreach( p.setProperty( "ImOutFile", _ ))
         p.setProperty( "HasImOutput", imagOut.isDefined.toString )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "Operator", (op match {
            case "+"       => 0
            case "*"       => 1
            case "/"       => 2
            case "%"       => 3
            case "pow"     => 4
            case "&"       => 5
            case "|"       => 6
            case "^"       => 7
            case "phase"   => 8
            case "mag"     => 9
            case "min"     => 10
            case "max"     => 11
            case "absmin"  => 12
            case "absmax"  => 13
            case "minsum"  => 14
            case "maxsum"  => 15
            case "minproj" => 16
            case "maxproj" => 17
            case "gate"    => 18
            case "atan"    => 19
         }).toString )
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Invert1", invert1.toString )
         p.setProperty( "Invert2", invert2.toString )
         p.setProperty( "Rectify1", rectify1.toString )
         p.setProperty( "Rectify2", rectify2.toString )
         p.setProperty( "DryMix", factorAmp( dryMix ))
         p.setProperty( "DryInvert", dryInvert.toString )
         p.setProperty( "WetMix", factorAmp( wetMix ))
         p.setProperty( "InGain1", dbAmp( drive1 ))
         p.setProperty( "InGain2", dbAmp( drive2 ))

         p.setProperty( "Offset1", absMsFactorTime( offset1 ))
         p.setProperty( "Offset2", absMsFactorTime( offset2 ))
         p.setProperty( "Length1", absMsFactorTime( length1 ))
         p.setProperty( "Length2", absMsFactorTime( length2 ))
      }
   }

   case class Bleach( in: String, fltIn: Option[ String ] = None, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      length: Int = 441, feedback: String = "-60.0dB", clip: String = "18.0dB",
      inverse: Boolean = false )
   extends Doc {
      def className = "Bleach"

      def write( p: Properties ) {
         p.setProperty( "AnaInFile", in )
         fltIn.foreach( p.setProperty( "FltInFile", _ ))
         p.setProperty( "UseAnaAsFilter", fltIn.isEmpty.toString )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Inverse", inverse.toString )
         p.setProperty( "FilterLength", par( length, Param.NONE ))
         p.setProperty( "FilterClip", dbAmp( clip ))
         p.setProperty( "FeedbackGain", dbAmp( feedback ))
      }
   }

   case class Concat( in1: String, in2: String, out: String, spec: AudioFileSpec = OutputSpec.aiffFloat,
                      gain: Gain = Gain.immediate, offset: String = "0.0s", length: String = "1.0",
                      overlap: String = "0.0", fade: String = "0.0", cross: String = "eqp" )
   extends Doc {
      def className = "Concat"

      def write( p: Properties ) {
         p.setProperty( "InputFile1", in1 )
         p.setProperty( "InputFile2", in2 )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "FadeType", (cross match {
            case "lin"   => 0
            case "eqp"   => 1
         }).toString )
         p.setProperty( "Offset", absMsFactorTime( offset ))
         p.setProperty( "Length", absRelMsFactorOffsetTime( length ))
         p.setProperty( "Overlap", absRelMsFactorOffsetTime( overlap ))
         p.setProperty( "Fade", absMsFactorTime( fade ))
      }
   }

   case class Convolution( in: String, impIn: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      mode: String = "conv", morphType: String = "rect", length: String = "full",
      truncFade: String = "0.01s", numIRs: Int = 1, winStep: String = "0.02s",
      overlap: String = "0s", normIRs: Boolean = false, trunc: Boolean = false,
      minPhase: Boolean = false )
   extends Doc {
      def className = "Convolution"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "ImpulseFile", impIn )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Mode", (mode match {
            case "conv"    => 0
            case "deconv"  => 1
            case "inv"     => 2
         }).toString )
         p.setProperty( "Policy", (morphType match {
            case "rect"    => 0
            case "polar"   => 1
         }).toString )
         p.setProperty( "Length", (length match {
            case "full"    => 0
            case "input"   => 1
            case "support" => 2
         }).toString )
         p.setProperty( "FadeLen", absMsTime( truncFade ))
         p.setProperty( "IRNumber", par( numIRs, Param.NONE ))
         p.setProperty( "WinStep", absMsTime( winStep ))
         p.setProperty( "WinOverlap", absMsTime( overlap ))
         p.setProperty( "NormImp", normIRs.toString )
         p.setProperty( "TruncOver", trunc.toString )
         p.setProperty( "Morph", (numIRs > 1).toString )
//         p.setProperty( "IRModEnv", x )
         p.setProperty( "MinPhase", minPhase.toString )
      }
   }

   /**
    * @param   mode     either of "up" or "down"
    * @param   chanUp   either of "min" or "max"
    * @param   chanDown either of "min" or "max"
    * @param   spacing  if None, this corresponds to original spacing, otherwise Some( timeSecs )
    */
   case class DrMurke( in: String, ctrlIn: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      mode: String = "up", chanUp: String = "min", chanDown: String = "max",
      threshUp: String = "0.3", threshDown: String = "0.2",
      durUp: String = "0.1s", durDown: String = "0.01s",
      attack: String = "0.01s", release: String = "1.0s",
      spacing: Option[ String ] = Some( "1.0s" ))
   extends Doc {
      def className = "DrMurke"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "CtrlFile", ctrlIn )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Mode", (mode match {
            case "up"   => 0
            case "down" => 1
         }).toString )
         p.setProperty( "ChannelUp", (chanUp match {
            case "max"  => 0
            case "min"  => 1
         }).toString )
         p.setProperty( "ChannelDown", (chanDown match {
            case "max"  => 0
            case "min"  => 1
         }).toString )
         p.setProperty( "SpacingType", (if( spacing.isDefined ) 0 else 1).toString )
         p.setProperty( "ThreshUp", factorAmp( threshUp ))
         p.setProperty( "ThreshDown", factorAmp( threshDown ))
         p.setProperty( "DurUp", absMsTime( durUp ))
         p.setProperty( "DurDown", absMsTime( durDown ))
         p.setProperty( "Attack", absMsTime( attack ))
         p.setProperty( "Release", absMsTime( release ))
         p.setProperty( "Spacing", absMsTime( spacing.getOrElse( "1.0s" )))
      }
   }

   case class Fourier( in: String, imagIn: Option[ String ] = None, out: String, imagOut: Option[ String ] = None,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      inverse: Boolean = false, format: String = "cartesian", trunc: Boolean = false,
      memory: Int = 16 )
   extends Doc {
      def className = "Fourier"

      def write( p: Properties ) {
         p.setProperty( "ReInFile", in )
         imagIn.foreach( p.setProperty( "ImInFile", _ ))
         p.setProperty( "HasImInput", imagIn.isDefined.toString )
         p.setProperty( "ReOutFile", out )
         imagOut.foreach( p.setProperty( "ImOutFile", _ ))
         p.setProperty( "HasImOutput", imagOut.isDefined.toString )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "Dir", (if( inverse ) 1 else 0).toString )
         p.setProperty( "Format", (format match {
            case "cartesian"  => 0
            case "polar"      => 1
         }).toString )
         p.setProperty( "Length", (if( trunc ) 1 else 0).toString )
         p.setProperty( "Memory", par( memory, Param.NONE ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
      }
   }

   case class Hilbert( in: String, out: String, imagOut: Option[ String ] = None,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      freq: Double = 0.0, antiAlias: Boolean = true, envelope: Boolean = false )
   extends Doc {
      def className = "Hilbert"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "ReOutFile", out )
         imagOut.foreach( p.setProperty( "ImOutFile", _ ))
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Mode", (
            if( envelope ) 3
            else if( freq == 0.0 ) 0
            else if( freq < 0.0 ) 2
            else 1
         ).toString )
         p.setProperty( "Freq", par( math.abs( freq ), Param.ABS_HZ ))
         p.setProperty( "AntiAlias", antiAlias.toString )
      }
   }

   case class Kriechstrom( in: String, out: String, spec: AudioFileSpec = OutputSpec.aiffFloat,
                           gain: Gain = Gain.immediate, length: String = "1.0", minChunks: Int = 4,
                           maxChunks: Int = 4, minRepeats: Int = 1, maxRepeats: Int = 1,
                           minChunkLen: String = "0.02s", maxChunkLen: String = "0.5s",
                           instantaneous: Boolean = true, maxEntry: String = "0.5s",
                           fades: String = "0.2", filterAmount: String = "0.0", filterColor: String = "neutral" )
   extends Doc {
      def className = "Kriechstrom"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "FltColor", (filterColor match {
            case "dark"    => 0
            case "neutral" => 1
            case "bright"  => 2
         }).toString )
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "LenUpdate", instantaneous.toString )
         p.setProperty( "MinChunkNum", par( minChunks, Param.NONE ))
         p.setProperty( "MaxChunkNum", par( maxChunks, Param.NONE ))
         p.setProperty( "MinChunkRep", par( minRepeats, Param.NONE ))
         p.setProperty( "MaxChunkRep", par( maxRepeats, Param.NONE ))
         p.setProperty( "MinChunkLen", absMsTime( minChunkLen ))
         p.setProperty( "MaxChunkLen", absMsTime( maxChunkLen ))
         p.setProperty( "CrossFade", absMsFactorTime( fades ))
         p.setProperty( "EntryPoint", absMsTime( maxEntry ))
         p.setProperty( "FltAmount", factorAmp( filterAmount ))
         p.setProperty( "OutLength", absMsFactorTime( length ))
//         p.setProperty( "KriechEnv", x )
      }
   }

   case class Laguerre( in: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      warp: Double = -0.1, frameSize: Int = 4, overlap: Int = 1 )
   extends Doc {
      def className = "Laguerre"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Warp", par( warp, Param.FACTOR ))
         p.setProperty( "FrameSize", (frameSize >> 6).toString )
         p.setProperty( "Overlap", (overlap - 1).toString )
      }
   }

   case class MakeLoop( in: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      length: String = "1s", offset: String = "#auto", trunc: String = "#auto",
      pos: String = "pre", /* shape: String = "normal",*/ cross: String = "eqp" )
   extends Doc {
      def className = "MakeLoop"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "FadePos", (pos match {
            case "pre"  => 0
            case "post" => 1
         }).toString )
//         p.setProperty( "FadeShape", (shape match {
//            case "normal"  => 0
//            case "fast"    => 1
//            case "slow"    => 2
//            case "easy"    => 3
//         }).toString )
         p.setProperty( "FadeType", (cross match {
            case "lin"  => 0
            case "eqp"  => 1
         }).toString )
         p.setProperty( "FadeLen", absMsFactorTime( length ))
         val offset0 = if( offset != "#auto" ) offset else if( pos == "pre" ) length else "0s"
         val trunc0  = if( trunc  != "#auto" ) trunc  else if( pos == "pre" ) "0s" else length
         p.setProperty( "InitialSkip", absMsFactorTime( offset0 ))
         p.setProperty( "FinalSkip", absMsFactorTime( trunc0 ))
      }
   }

   case class Needlehole( in: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      filter: String = "median", length: String = "0.05s", thresh: String = "-18dB", subDry: Boolean = false )
   extends Doc {
      def className = "Needlehole"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Filter", (filter match {
            case "median"  => 0
            case "stddev"  => 1
            case "min"     => 2
            case "center"  => 3
         }).toString )
         p.setProperty( "Length", absMsTime( length ))
         p.setProperty( "Thresh", factorDBAmp( thresh ))
         p.setProperty( "SubDry", subDry.toString )
      }
   }

   case class Resample( in: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      rate: String = "0semi", keepHeader: Boolean = false, interpolate: Boolean = false,
      fltLength: String = "medium" )
   extends Doc {
      def className = "Resample"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Quality", (fltLength match {
            case "short"   => 0
            case "medium"  => 1
            case "long"    => 2
         }).toString )
         p.setProperty( "Rate", absRelHzSemiFreq( rate ))
         p.setProperty( "KeepHeader", keepHeader.toString )
         p.setProperty( "Interpole", interpolate.toString )
      }
   }

   case class Rotation( in: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      mode: String = "rotate", numRepeats: Int = 2, subDry: Boolean = false )
   extends Doc {
      def className = "Rotation"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Mode", (mode match {
            case "rotate"  => 0
            case "repeat"  => 1
         }).toString )
         p.setProperty( "Repeats", par( numRepeats, Param.NONE ))
         p.setProperty( "SubDry", subDry.toString )
      }
   }

   // XXX SpectPatch

   case class StepBack( in: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      mode: String = "decon", corrLen: Int = 1024, corrStep: Int = 256, /* corrFine: Int = 32, */
      minSpacing: String = "0.1s", maxSpacing: String = "5.0s", minXFade: String = "0.001s", maxXFade: String ="1.0s",
      offset: String = "0s", weight: Double = 0.5, markers: Boolean = false )
   extends Doc {
      def className = "StepBack"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Mode", (mode match {
            case "decon"   => 0
            case "random"  => 1
            case "recon"   => 2
            case "forward" => 3
         }).toString )
         p.setProperty( "CorrLength", (math.log( 131072 / corrLen ) / math.log( 2 )).toInt.toString )
         p.setProperty( "CorrStep",  (math.log( 131072 / corrStep ) / math.log( 2 )).toInt.toString )
         p.setProperty( "MinSpacing", absMsTime( minSpacing ))
         p.setProperty( "MaxSpacing", absMsTime( maxSpacing ))
         p.setProperty( "MinXFade", absMsTime( minXFade ))
         p.setProperty( "MaxXFade", absMsTime( maxXFade ))
         p.setProperty( "Offset", offsetMsTime( offset ))
         p.setProperty( "Weight", par( weight * 100, Param.FACTOR_AMP ))
         p.setProperty( "Markers", markers.toString )
      }
   }

   case class Voocooder( in: String, mod: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      op: String = "*", fltLength: String = "short",
      loFreq: String = "400Hz", hiFreq: String = "11025Hz", /* dryMix: Double = 1.0, wetMix: Double = 0.25, */
      /* rollOff: String = "12semi",*/ bandsPerOct: Int = 12 )
   extends Doc {
      def className = "Voocooder"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "ModFile", mod )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Kombi", (op match {
            case "*"       => 0
            case "%"       => 1
            case "min"     => 2
            case "max"     => 3
            case "vocoder" => 4
         }).toString )
         p.setProperty( "FilterLen", (fltLength match {
            case "short"      => 0
            case "medium"     => 1
            case "long"       => 2
            case "verylong"   => 3
         }).toString )
         p.setProperty( "LoFreq", absHzFreq( loFreq ))
         p.setProperty( "HiFreq", absHzFreq( hiFreq ))
         p.setProperty( "BandsPerOct", par( bandsPerOct, Param.NONE ))
      }
   }

   case class UnaryOp( in: String, imagIn: Option[ String ] = None, out: String, imagOut: Option[ String ] = None,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      offset: String = "0.0", length: String = "1.0", op: String = "thru",
      drive: String = "0.0dB", rectify: Boolean = false, invert: Boolean = false, reverse: Boolean = false,
      dryMix: String = "0.0", dryInvert: Boolean = false, wetMix: String = "1.0" )
   extends Doc {
      def className = "UnaryOp"

      def write( p: Properties ) {
         p.setProperty( "ReInFile", in )
         imagIn.foreach( p.setProperty( "ImInFile", _ ))
         p.setProperty( "HasImInput", imagIn.isDefined.toString )
         p.setProperty( "ReOutFile", out )
         imagOut.foreach( p.setProperty( "ImOutFile", _ ))
         p.setProperty( "HasImOutput", imagOut.isDefined.toString )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "Operator", (op match {
            case "thru"       => 0
            case "sin"        => 1
            case "squared"    => 2
            case "sqrt"       => 3
            case "log"        => 4
            case "exp"        => 5
            case "rectpolar"  => 6
            case "rectpolar_unwrapped"=> 7
            case "polarrect"  => 8
            case "not"        => 9
         }).toString )
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
         p.setProperty( "Invert", invert.toString )
         p.setProperty( "Reverse", reverse.toString )
         p.setProperty( "DryMix", factorAmp( dryMix ))
         p.setProperty( "DryInvert", dryInvert.toString )
         p.setProperty( "WetMix", factorAmp( wetMix ))
         p.setProperty( "InGain", dbAmp( drive ))

         p.setProperty( "Offset", absMsFactorTime( offset ))
         p.setProperty( "Length", absMsFactorTime( length ))
      }
   }

   case class Wavelet( in: String, out: String,
      spec: AudioFileSpec = OutputSpec.aiffFloat, gain: Gain = Gain.immediate,
      filter: String = "daub4", inverse: Boolean = false, trunc: Boolean = false,
      scaleGain: String = "3dB" )
   extends Doc {
      def className = "Wavelet"

      def write( p: Properties ) {
         p.setProperty( "InputFile", in )
         p.setProperty( "OutputFile", out )
         p.setProperty( "OutputType", audioFileType( spec ))
         p.setProperty( "OutputReso", audioFileRes( spec ))
         p.setProperty( "Dir", (if( inverse ) 1 else 0).toString )
         p.setProperty( "Filter", (filter match {
            case "daub4"   => 0
            case "daub6"   => 1
            case "daub8"   => 2
            case "daub10"  => 3
            case "daub12"  => 4
            case "daub14"  => 5
            case "daub16"  => 6
            case "daub18"  => 7
            case "daub20"  => 8
         }).toString )
         p.setProperty( "Length", (if( trunc ) 1 else 0).toString )
         p.setProperty( "ScaleGain", dbAmp( scaleGain ))
         p.setProperty( "GainType", gainType( gain ))
         p.setProperty( "Gain", dbAmp( gain.value ))
      }
   }

   // ---- helper ----

   private def absMsFactorTime( s: String ) : String = {
      if( s.endsWith( "s" )) absMsTime( s ) else factorTime( s )
   }

   private def absRelMsFactorOffsetTime( s: String ) : String = {
      if( s.endsWith( "s" )) absRelMsTime( s ) else factorOffsetTime( s )
   }

   private def par( value: Double, unit: Int ) : String = Param( value, unit ).toString

   private def absRelHzSemiFreq( s: String ) : String = {
      if( s.endsWith( "semi" )) semiFreq( s )
      else if( s.endsWith( "Hz" )) {
         if( s.startsWith( "+" ) || s.startsWith( "-" )) relHzFreq( s ) else absHzFreq( s )
      } else offsetFreq( s )
   }

   private def semiFreq( s: String ) : String = {
      require( s.endsWith( "semi" ))
      Param( s.substring( 0, s.length - 4 ).toDouble, Param.OFFSET_SEMITONES ).toString
   }

   private def relHzFreq( s: String ) : String = {
      require( s.endsWith( "Hz" ))
      Param( s.substring( 0, s.length - 2 ).toDouble, Param.OFFSET_HZ ).toString
   }

   private def absHzFreq( s: String ) : String = {
      require( s.endsWith( "Hz" ))
      Param( s.substring( 0, s.length - 2 ).toDouble, Param.ABS_HZ ).toString
   }

   private def offsetFreq( s: String ) : String = {
      val s0 = if( s.startsWith( "+" )) s.substring( 1 ) else s
      Param( s0.toDouble * 100, Param.OFFSET_FREQ ).toString
   }

   private def dbAmp( s: String ) : String = {
      require( s.endsWith( "dB" ))
      Param( s.substring( 0, s.length - 2 ).toDouble, Param.DECIBEL_AMP ).toString
   }

   private def factorDBAmp( s: String ) : String = {
      if( s.endsWith( "dB" )) dbAmp( s ) else factorAmp( s )
   }

   private def factorAmp( s: String ) : String = {
      Param( s.toDouble * 100, Param.FACTOR_AMP ).toString
   }

   private def factorOffsetTime( s: String ) : String = {
      if( s.startsWith( "+" ) || s.startsWith( "-" )) offsetTime( s ) else factorTime( s )
   }

   private def offsetTime( s: String ) : String = {
      val s0 = if( s.startsWith( "+" )) s.substring( 1 ) else s
      Param( s0.toDouble * 100, Param.OFFSET_TIME ).toString
   }

   private def factorTime( s: String ) : String = {
      Param( s.toDouble * 100, Param.FACTOR_TIME ).toString
   }

   private def absRelMsTime( s: String ) : String = {
      if( s.startsWith( "+" ) || s.startsWith( "-" )) offsetMsTime( s ) else absMsTime( s )
   }

   private def absMsTime( s: String ) : String = {
      require( s.endsWith( "s" ))
      Param( s.substring( 0, s.length - 1 ).toDouble * 1000, Param.ABS_MS ).toString
   }

   private def offsetMsTime( s: String ) : String = {
      require( s.endsWith( "s" ))
      val i = if( s.startsWith( "+" )) 1 else 0
      Param( s.substring( i, s.length - 1 ).toDouble * 1000, Param.OFFSET_MS ).toString
   }

   private def gainType( gain: Gain ) : String = (if( gain.normalized ) 0 else 1).toString

   private def audioFileType( spec: AudioFileSpec ) : String =
      (spec.fileType match {
         case AudioFileType.AIFF    => 0
         case AudioFileType.NeXT    => 1
         case AudioFileType.IRCAM   => 2
         case AudioFileType.Wave    => 3
         case AudioFileType.Wave64  => 4
         case other                 => sys.error(s"Currently unsupported file type $other")
      }).toString

   private def audioFileRes( spec: AudioFileSpec ) : String =
      (spec.sampleFormat match {
         case SampleFormat.Int16 => 0
         case SampleFormat.Int24 => 1
         case SampleFormat.Float => 2
         case SampleFormat.Int32 => 3
      }).toString

   private case class Connect( timeOut: Double, fun: Boolean => Unit )
   private case class Process( name: String, doc: Doc, fun: Boolean => Unit, progress: Int => Unit )
   private case class ConnectSucceeded( c: Client )
   private case object ConnectFailed
   private case object Pause
   private case object Resume
   private case class DumpOSC( onOff: Boolean )
   private case class JobDone( id: Int, success: Boolean )
   private case class DocOpen( path: String )
   private case class DocOpenSucceeded( path: String, id: AnyRef, progress: Int => Unit )
   private case class DocOpenFailed( path: String )

   private def printInfo( msg: String ) {
      println( "" + new java.util.Date() + " : FScape : " + msg )
   }

   private def protect( code: => Unit ) {
      try {
         code
      } catch {
         case e: Throwable => e.printStackTrace()
      }
   }

   private def warn( what: String ) {
      printInfo( "Warning - " + what )
   }
}

class FScapeJobs private(transport: Transport.Net, addr: InetSocketAddress, numThreads: Int) {
  import FScapeJobs._

  @volatile var verbose = false

  /**
   * A switch to indicate whether FScape should (`true`) open GUI windows for
   * the jobs processed or not (`false`).
   */
  @volatile var openWindows = false
  //   var maxJobs       = 1000

  /**
   * Adds a new job to the server queue. Note that when there are several
   * threads, this may complete even before previously scheduled processes.
   * Hence, to make sure processes are carried out sequentially, use
   * `processChain` instead.
   *
   * @param name the name of the job, which is arbitrary and is used for logging purposes only
   * @param doc the FScape document to render
   * @param fun the function to execute upon job failure or completion. The function is
   *            called with `true` upon success, and `false` upon failure.
   */
  def process(name: String, doc: Doc, progress: Int => Unit = (i: Int) => ())(fun: Boolean => Unit) {
    MainActor ! Process(name, doc, fun, progress)
  }

  /**
   * Adds a chain ob jobs to the queue. The jobs are still processed sequentially,
   * even if the number of parallel threads is not exhausted,
   * however the completion function is only called after all jobs of the chain have
   * completed or a failure has occurred.
   */
  def processChain(name: String, docs: Seq[Doc], progress: Int => Unit = (i: Int) => ())(fun: Boolean => Unit) {
    docs.headOption.map(doc => process(name, doc, progress) {
      success =>
        if (success) {
          processChain(name, docs.tail, progress)(fun)
        } else {
          fun(false)
        }
    }).getOrElse(fun(true))
  }

  def connect(timeOut: Double = 20.0)(fun: Boolean => Unit) {
    MainActor ! Connect(timeOut, fun)
  }

  def pause() {
    MainActor ! Pause
  }

  def resume() {
    MainActor ! Resume
  }

  def dumpOSC(onOff: Boolean) {
    MainActor ! DumpOSC(onOff)
  }

  private def inform(what: => String) {
    if (verbose) printInfo(what)
  }

  private class Launcher(timeOut: Double = 20.0) extends Thread {
    //      start()
    override def run() {
      if (verbose) printInfo("Launcher started")
      Thread.sleep(1000) // 5000
      val c = transport match {
        case TCP => TCP.Client(addr)
        case UDP => UDP.Client(addr)
      }
      var count = (timeOut + 0.5).toInt
      var ok = false
      while (count > 0 && !ok) {
        count -= 1
        try {
          c.connect()
          // c.action = (msg, addr, when) => MainActor ! msg
          MainActor ! ConnectSucceeded(c)
          ok = true
          if (verbose) printInfo("Connect succeeded")
        }
        catch {
          case e: Throwable =>
            if (verbose) printInfo("Connect failed. Sleep")
            Thread.sleep(1000)
            // reactWithin( 1000 ) { case TIMEOUT => }
        }
      }
      if (!ok) MainActor ! ConnectFailed
    }
  }

  private object MainActor extends DaemonActor {
    start()

    def act() {
      loop {
        react {
          case Connect(timeOut, fun) =>
            val l = new Launcher(timeOut)
            l.start()
            react {
              case ConnectSucceeded(c) =>
                protect(fun(true))
                actClientReady(c)
              case ConnectFailed => protect(fun(false))
            }
        }
      }
    }

    def actClientReady(client: Client) {
      inform("ClientReady received")

      val actors    = IIdxSeq.tabulate(numThreads)(id => new JobActor(id, client))
      var actorMap  = IntMap.empty[JobOrg]
      var pathMap   = Map.empty[String, JobOrg]
      var paused    = false
      val procs     = MQueue[Process]()

      def checkProcs() {
        var foundIdle = true
        while (foundIdle && !paused && procs.nonEmpty) {
          val actorO = actors.find(a => !actorMap.contains(a.id))
          foundIdle = actorO.isDefined
          actorO.foreach { actor =>
            val proc = procs.dequeue()
            try {
              val path = File.createTempFile("tmp", ".fsc").getAbsolutePath
              val prop = new Properties()
              prop.setProperty("Class", "de.sciss.fscape.gui." + proc.doc.className + "Dlg")
              proc.doc.write(prop)
              val os = new FileOutputStream(path)
              // prop.store(os, "FScapeJobs")
              prop.store(os, "Created by FScape; do not edit manually!")
              os.close()
              val org = JobOrg(actor.id, proc, path)
              actorMap += actor.id -> org
              pathMap += path -> org
              actor ! DocOpen(path)
              client ! Message("/doc", "open", path, if (openWindows) 1 else 0)
            } catch {
              case e: Throwable =>
                warn("Caught exception:")
                e.printStackTrace()
                protect(proc.fun(false))
            }
          }
        }
      }

      client.action = {
        case msg @ Message("/query.reply", sid: Int, ignore@_*) =>
          val aid = sid >> 24
          if (aid >= 0 && aid < actors.size) actors(aid) ! msg // forward to appropriate job actor
        case Message("/done", "/doc", "open", path: String, id: AnyRef, ignore@_*) =>
          pathMap.get(path).foreach {
            org =>
            //                  actorMap -= org.actorID
            //                  pathMap  -= path
              actors(org.actorID) ! DocOpenSucceeded(path, id, org.proc.progress)
          }
        case Message("/failed", "/doc", "open", path: String, ignore@_*) =>
          pathMap.get(path).foreach {
            org =>
            //                  actorMap -= org.actorID
            //                  pathMap  -= path
              actors(org.actorID) ! DocOpenFailed(path)
          }
        case _ =>
      }

      loop {
        react {
          case Connect(timeOut, fun) =>
            warn("Already connected")
            protect(fun(true))
          case p: Process =>
            procs.enqueue(p)
            checkProcs()
          case JobDone(id, success) =>
            actorMap.get(id) match {
              case Some(org) =>
                actorMap -= id
                pathMap -= org.path
                protect(org.proc.fun(success))
              case None =>
                warn("Spurious job actor reply : " + id)
            }
            checkProcs()
          case Pause =>
            inform("paused")
            paused = true
          case Resume =>
            inform("resumed")
            paused = false
            checkProcs()
          case DumpOSC(onOff) =>
            client.dump(if (onOff) Dump.Text else Dump.Off)
          case m => warn("? Illegal message: " + m)
        }
      }
    }

    case class JobOrg(actorID: Int, proc: Process, path: String)
  }

  private class JobActor(val id: Int, client: Client) extends DaemonActor {
    val prefix = "[" + id + "] "
    val clientMask = id << 24
    var syncID = -1 // accessed only in actor, incremented per query

    start()

    def act() {
      loop {
        react {
          case DocOpen(path) => reactWithin(10000L) {
            case TIMEOUT =>
              warn(prefix + "Timeout while trying to open document (" + path + ")")
              MainActor ! JobDone(id, success = false)
            case DocOpenFailed(`path`) =>
              warn(prefix + "Failed to open document (" + path + ")")
              MainActor ! JobDone(id, success = false)
            case DocOpenSucceeded(`path`, docID, progress) =>
              inform(prefix + "document opened (" + name + ")")
              actProcess(name, docID, progress) {
                success =>
                  MainActor ! JobDone(id, success)
              }
          }
        }
      }
    }

    def actProcess(name: String, docID: AnyRef, progress: Int => Unit)(fun: Boolean => Unit) {
      try {
        def timedOut(msg: Message) {
          warn(prefix + "TIMEOUT (" + name + " -- " + msg + ")")
          fun(false)
        }

        def query(path: String, properties: Seq[String], timeOut: Long = 4000L)(handler: Seq[Any] => Unit) {
          syncID += 1
          val sid = syncID | clientMask
          val msg = Message(path, ("query" +: sid +: properties): _*)
          client ! msg
          reactWithin(timeOut) {
            case TIMEOUT => timedOut(msg)
            case Message("/query.reply", `sid`, values@_*) => handler(values)
          }
        }

        val addr = "/doc/id/" + docID
        client ! Message(addr, "start")
        query("/main", "version" :: Nil) {
          // tricky sync
          case _ => {
            var progPerc  = 0
            var running   = 1
            var err       = ""

            loopWhile(running != 0) {
              reactWithin(1000L) {
                case TIMEOUT => query(addr, "running" :: "progression" :: "error" :: Nil) {
                  case Seq(r: Int, p: Float, e: String) => {
                    running = r
                    err = e
                    val perc = (p * 100).toInt
                    if (perc != progPerc) {
                      progPerc = perc
                      progress(perc)
                    }
                  }
                }
              }
            } andThen {
              client ! Message(addr, "close")
              if (err != "") {
                warn(prefix + "ERROR (" + name + " -- " + err + ")")
                fun(false)
              } else {
                inform(prefix + "Success (" + name + ")")
                fun(true)
              }
            }
          }
        }
      } catch {
        case e: IOException =>
          warn(prefix + "Caught exception : " + e)
          fun(false)
      }
    }
  }
}