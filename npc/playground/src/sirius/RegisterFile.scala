package sirius

import chisel3._

class RegisterFile(implicit private val cfg: CoreConfig) extends Module {
  val iduIn = IO(Flipped(new IduToRegFileIO))
  val wbuIn = IO(Flipped(new WbuToRegFileIO))
  val debug = Option.when(cfg.isDebug)(IO(Output(Vec(cfg.registerNum, UInt(cfg.xlen.W)))))
  // val debug = IO(Output(Vec(cfg.registerNum, UInt(cfg.xlen.W))))

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
  // debug := regFile

  // val regFile = Mem(cfg.registerNum, UInt(cfg.xlen.W))
  // when(wbuIn.wEn && (wbuIn.wAddr =/= 0.U)) {
  //   regFile.write(wbuIn.wAddr, wbuIn.wData)
  // }
  // iduIn.rData(0) := regFile.read(iduIn.rAddr(0))
  // iduIn.rData(1) := regFile.read(iduIn.rAddr(1))
  // when (iduIn.rAddr(0) === 0.U) {
  //   iduIn.rData(0) := 0.U
  // }
  // when (iduIn.rAddr(1) === 0.U) {
  //   iduIn.rData(1) := 0.U
  // }
}
