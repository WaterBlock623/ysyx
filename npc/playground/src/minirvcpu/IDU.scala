package playground.minirvcpu

import chisel3._
import chisel3.util.experimental.decode._
import common.minirvcpu._

// 指令译码Module
class IDU(implicit private val cfg: CoreConfig) extends Module {
  val inst = IO(Input(UInt(cfg.xlen.W)))

  val decodeCollector = InstDecodeCollector()
  val ctrlSignals = IO(new CtrlSignals(decodeCollector.allFields))

  val decodeTable = new DecodeTable(decodeCollector.allPatterns, decodeCollector.allFields)
  val decodeResult = decodeTable.decode(inst)
  decodeCollector.allFields.foreach { f =>
    ctrlSignals.elements(f.stage).asInstanceOf[Record].elements(f.name) := 
      decodeResult(f.asInstanceOf[DecodeField[_, _ <: Data]])
  }
}


