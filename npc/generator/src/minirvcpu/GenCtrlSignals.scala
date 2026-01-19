package generator.minirvcpu

import generator.util.BundleGenerator
import common.minirvcpu.InstDecodeCollector

object GenCtrlSignals extends App {
  val collector = InstDecodeCollector()
  val gen = new BundleGenerator("playground.minirvcpu", "CtrlSignals", collector.allFields) 
  gen.generate(args(0))
}
