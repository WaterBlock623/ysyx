package minirvcpu

import chisel3._

class IfuSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val inst = Output(UInt(cfg.xlen.W))
  val memRAddr = Output(UInt(cfg.memoryAddrWidth.W))
}

class Ifu(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val pcRegisterIn = Flipped(new PcRegisterSignals)
    val lsuIn = Flipped(new LsuSignals)
    val ifuOut = new IfuSignals
  })

  io.ifuOut.memRAddr := io.pcRegisterIn.pc(cfg.memoryAddrWidth - 1, 0)
  io.ifuOut.inst := io.lsuIn.rData(0)
}
