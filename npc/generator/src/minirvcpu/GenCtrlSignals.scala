package minirvcpu.util

import cpu.util.BundleGenerator
import minirvcpu.util.InstDecodeCollector

object GenCtrlSignals extends App {
  val collector = InstDecodeCollector()
  val gen = new BundleGenerator("minirvcpu.util", "CtrlSignals", collector.allFields) 
  gen.generate("playground/src/minirvcpu/build/")
}
