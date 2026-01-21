package minirvcpu

import chisel3._

class LSUSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val rData = Vec(2, UInt(cfg.xlen.W))
}

class LSU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val ifuIn = Flipped(new IFUSignals)
    val lsuOut = new LSUSignals
  })
  

}
