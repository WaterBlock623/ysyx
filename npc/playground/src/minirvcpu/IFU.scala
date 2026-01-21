package minirvcpu

import chisel3._

class IFUSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val inst = Output(UInt(cfg.xlen.W))
  val memRAddr = Output(UInt(cfg.memoryAddrWidth.W))
}

class IFU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val pcRegisterIn = Flipped(new PcRegisterSignals)
    val lsuIn = Flipped(new LSUSignals)
    val ifuOut = new IFUSignals
  })

  io.ifuOut.memRAddr := io.pcRegisterIn.pc(cfg.memoryAddrWidth - 1, 0)
  io.ifuOut.inst := io.lsuIn.rData(0)
}
