package generator.minirvcpu

import generator.util.BundleGenerator
import common.InstDecodeCollector

object GenCtrlSignals extends App {
  val collector = InstDecodeCollector()
  val gen = new BundleGenerator("minirvcpu.util", "CtrlSignals", collector.allFields) 
  gen.generate(args(0))
}
