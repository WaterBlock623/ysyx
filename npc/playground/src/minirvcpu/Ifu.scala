package minirvcpu

import chisel3._

class IfuSignals(
  implicit private val cfg: CoreConfig)
    extends Bundle {
  val inst = Output(UInt(cfg.xlen.W))
}

class Ifu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val io = IO(new Bundle {
    val pcRegisterIn = Flipped(new PcRegisterSignals)
    val memInstFetchIO = Flipped(new MemInstFetchIO)
    val ifuOut = new IfuSignals
  })

  io.memInstFetchIO.rAddr := io.pcRegisterIn.pc(cfg.memoryAddrWidth - 1, 0)
  io.ifuOut.inst := io.memInstFetchIO.rData
}
