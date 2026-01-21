package minirvcpu

import chisel3._

class LsuSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val rData = Vec(2, UInt(cfg.xlen.W))
}

class Lsu(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val ifuIn = Flipped(new IfuSignals)
    val lsuOut = new LsuSignals
  })
  

}
