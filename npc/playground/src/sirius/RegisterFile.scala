package sirius

import chisel3._

class RegisterFile(implicit private val cfg: CoreConfig) extends Module {
  val iduIn = IO(Flipped(new IduToRegFileIO))
  val wbuIn = IO(Flipped(new WbuToRegFileIO))
  val debug = if (cfg.isDebug) Some(IO(Output(Vec(cfg.registerNum, UInt(cfg.xlen.W))))) else None

  val regFile = Reg(Vec(cfg.registerNum, UInt(cfg.xlen.W)))
  when(wbuIn.wEn) {
    regFile(wbuIn.wAddr) := wbuIn.wData
  }
  regFile(0) := 0.U
  iduIn.rData(0) := regFile(iduIn.rAddr(0))
  iduIn.rData(1) := regFile(iduIn.rAddr(1))

  if (cfg.isDebug) {
    debug.get := regFile
  }
}
