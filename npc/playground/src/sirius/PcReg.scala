package sirius

import chisel3._

class PcReg(
  implicit private val cfg: CoreConfig)
    extends Module {
  val ifuIn = IO(Flipped(new IfuToPcRegIO))
  val wbuIn = IO(Flipped(new WbuToPcRegIO))
  val debug = Option.when(cfg.isDebug)(IO(new Bundle {
    val pc = Output(UInt(cfg.xlen.W))
  }))

  val pcReg = RegInit(cfg.pcInit.U(cfg.xlen.W))
  when (ifuIn.ready) {
    pcReg := pcReg + 4.U
  }
  when (wbuIn.isJump) {
    pcReg := wbuIn.target
  }
  ifuIn.pc := pcReg

  if (cfg.isDebug) {
    debug.get.pc := pcReg
  }
}
