package sirius

import chisel3._

class PcReg(
  implicit private val cfg: CoreConfig)
    extends Module {
  val ifuIn = IO(Flipped(new IfuToPcRegIO))
  val wbuIn = IO(Flipped(new WbuToPcRegIO))

  val pcReg = RegInit("h80000000".U(cfg.xlen.W))
  val pcNext = Mux(wbuIn.isJump, wbuIn.target, pcReg + 4.U)
  pcReg := pcNext
  ifuIn.pc := pcReg
}
