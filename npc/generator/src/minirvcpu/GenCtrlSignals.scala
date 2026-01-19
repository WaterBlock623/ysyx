package minirvcpu

import util.BundleGenerator

object GenCtrlSignals extends App {
  val collector = InstDecodeCollector()
  val gen = new BundleGenerator("minirvcpu", "CtrlSignals", collector.allFields) 
  gen.generate(args(0) + "CtrlSignals.scala")
}
