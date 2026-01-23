package sirius

import chisel3._
import chisel3.util.MuxLookup

// gpr
class RegisterFile(implicit private val cfg: CoreConfig) extends Module {
  val iduIn = IO(Flipped(new IduToRegFileIO))
  val wbuIn = IO(Flipped(new WbuToRegFileIO))

  val regFile = Reg(Vec(cfg.registerNum, UInt(cfg.xlen.W)))
  when(wbuIn.wEn) {
    regFile(wbuIn.wAddr) := wbuIn.wData
  }
  regFile(0) := 0.U
  iduIn.rData(0) := regFile(iduIn.rAddr(0))
  iduIn.rData(1) := regFile(iduIn.rAddr(1))
}

// pc
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

// 控制pc跳转和gpr读写
class Wbu(implicit private val cfg: CoreConfig) extends Module {
  val exte = IO(new Bundle {
    val pcReg = new WbuToPcRegIO
    val regFlie = new WbuToRegFileIO
  })
  val in = IO(Flipped(new LsuToWbuIO))

  val ctrl = in.ctrl.wbuCtrl
  val pcReg = exte.pcReg
  val regFile = exte.regFlie
  val imm = in.lsuPayload.idu.imm
  val aluOut = in.lsuPayload.exu.aluOut

  // pc
  pcReg.target := MuxLookup(ctrl.jumpTargetSel, imm)(
    Seq(
      JumpTargetSelEnum.imm.asUInt -> imm,
      JumpTargetSelEnum.alu.asUInt -> aluOut
    )
  )
  pcReg.isJump := ctrl.isJump || (ctrl.isBranch && aluOut(0))

  // gpr
  regFile.wAddr := in.lsuPayload.idu.wAddr
  val pc = in.lsuPayload.ifu.pc
  val loadData = in.lsuPayload.lsu.loadData
  regFile.wEn := ctrl.isWriteBackReg
  regFile.wData := MuxLookup(ctrl.writeBackSel, aluOut)(
    Seq(
      WriteBackSelEnum.alu.asUInt -> aluOut,
      WriteBackSelEnum.imm.asUInt -> imm,
      WriteBackSelEnum.staticNextPc.asUInt -> (pc + 4.U),
      WriteBackSelEnum.lsu.asUInt -> loadData,
    )
  )
}
