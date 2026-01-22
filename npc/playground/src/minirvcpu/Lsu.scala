package minirvcpu

import chisel3._

class MemInstFetchIO(
  implicit private val cfg: CoreConfig)
    extends Bundle {
  val rAddr = Input(UInt(cfg.memoryAddrWidth.W))
  val rData = Output(UInt(cfg.xlen.W))
}

class MemLoadStoreIO(
  implicit private val cfg: CoreConfig)
    extends Bundle {
  val valid = Input(Bool())
  val rAddr = Input(UInt(cfg.memoryAddrWidth.W))
  val rData = Output(UInt(cfg.xlen.W))
  val wAddr = Input(UInt(cfg.memoryAddrWidth.W))
  val wData = Input(UInt(cfg.xlen.W))
  val wMask = Input(UInt((cfg.xlen >> 3).W))
  val wEn = Input(Bool())
}
