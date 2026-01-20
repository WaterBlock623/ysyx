package minirvcpu

import chisel3._

class IFU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val pc = Input(UInt(cfg.xlen.W))
    val memWData = Input(UInt(cfg.xlen.W))
    val memWAddr = Output(UInt(cfg.memoryAddrWidth.W))
    val inst = Output(UInt(cfg.xlen.W))
  })

  io.memWAddr := io.pc(cfg.memoryAddrWidth - 1, 0)
  io.inst := io.memWData
}
