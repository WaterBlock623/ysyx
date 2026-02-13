package sirius

import chisel3._
import chisel3.util.MuxLookup

// 控制pc跳转和gpr读写
class Wbu(implicit private val cfg: CoreConfig) extends Module {
  val exte = IO(new Bundle {
    val pcReg = new WbuToPcRegIO
    val regFlie = new WbuToRegFileIO
    val csr = new WbuToCsrIO
  })
  val in = IO(Flipped(new LsuToWbuIO))

  val ctrl = in.ctrl.wbuCtrl
  val pcReg = exte.pcReg
  val regFile = exte.regFlie
  val imm = in.lsuPayload.idu.imm
  val aluOut = in.lsuPayload.exu.aluOut
  val csrData = in.lsuPayload.exu.csrData

  // csr作为跳转地址
  val csrJumpTarget = MuxLookup(in.ctrl.wbuCtrl.jumpTargetSel, exte.csr.mepc)(
    Seq(
      JumpTargetSelEnum.mtvec.asUInt -> exte.csr.mtvec,
      JumpTargetSelEnum.mepc.asUInt -> exte.csr.mepc
    )
  )

  // pc
  val normalJumpTarget = in.lsuPayload.exu.jumpTarget
  pcReg.target := Mux(ctrl.isFromCsr, csrJumpTarget, normalJumpTarget)
  pcReg.isJump := ctrl.isJump || ctrl.isFromCsr || (ctrl.isBranch && aluOut(0))

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
      WriteBackSelEnum.csr.asUInt -> csrData,
    )
  )

  // csr
  val csr = exte.csr
  csr.wEn := in.ctrl.wbuCtrl.isWriteBackCsr && (imm || !in.ctrl.isCsrWriteCheck)
  csr.wAddr := in.lsuPayload.idu.csrAddr
  csr.wData := in.lsuPayload.exu.aluOut

  csr.pc := pc
  csr.isTrap := in.ctrl.wbuCtrl.isEcall
  csr.causeNum := 11.U

}
