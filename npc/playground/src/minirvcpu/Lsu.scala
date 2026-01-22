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

class LsuSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val loadData = Output(UInt(cfg.xlen.W))
}

class Lsu(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val memLoadStoreIO = Flipped(new MemLoadStoreIO)
    val iduIn = Flipped(new IduSignals)
    val exuIn = Flipped(new ExuSignals)
    val registerFileIn = Flipped(new RegisterFileSignals)
    val lsuOut = new LsuSignals
  })

  val ctrlSig = io.iduIn.ctrlSignals.ls
  io.memLoadStoreIO.valid := ctrlSig.isLoad || ctrlSig.isStore
  io.memLoadStoreIO.wEn := ctrlSig.isStore
  val addr = io.exuIn.aluResult
  io.memLoadStoreIO.rAddr := addr
  io.memLoadStoreIO.wAddr := addr

}
