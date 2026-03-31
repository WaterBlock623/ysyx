package sirius

import chisel3._
import chisel3.util.MuxLookup
import chisel3.util.Decoupled

// 控制pc跳转和gpr读写
class Wbu(implicit private val cfg: CoreConfig) extends Module {
  val exte = IO(new Bundle {
    val pcReg = new WbuToPcRegIO
    val regFlie = new WbuToRegFileIO
    val csr = new WbuToCsrIO
  })
  val in = IO(Flipped(Decoupled(new LsuToWbuIO)))
  val out = IO(Output(new Bundle {
    val valid = Bool()
  }))

  // DecoupledIO
  DecoupledFsm(false, in)
  in.ready := true.B
  out.valid := in.valid
  dontTouch(out)
  val inBits = in.bits

  val ctrl = inBits.ctrl.wbuCtrl
  val pcReg = exte.pcReg
  val regFile = exte.regFlie
  val imm = inBits.lsuPayload.idu.imm
  val aluOut = inBits.lsuPayload.exu.aluOut
  val csrData = inBits.lsuPayload.exu.csrData

  // csr作为跳转地址
  val csrJumpTarget = MuxLookup(inBits.ctrl.wbuCtrl.jumpTargetSel, exte.csr.mepc)(
    Seq(
      JumpTargetSelEnum.mtvec.asUInt -> exte.csr.mtvec,
      JumpTargetSelEnum.mepc.asUInt -> exte.csr.mepc
    )
  )

  // pc
  val normalJumpTarget = inBits.lsuPayload.exu.jumpTarget
  pcReg.target := Mux(inBits.lsuPayload.trap.isTrap, exte.csr.mtvec, Mux(ctrl.isFromCsr, csrJumpTarget, normalJumpTarget))
  pcReg.isJump := ctrl.isJump || ctrl.isFromCsr || (ctrl.isBranch && aluOut(0)) || inBits.lsuPayload.trap.isTrap
  pcReg.wEn := in.valid

  // gpr
  regFile.wAddr := inBits.lsuPayload.idu.wAddr
  val pc = inBits.lsuPayload.ifu.pc
  val loadData = inBits.lsuPayload.lsu.loadData
  regFile.wEn := ctrl.isWriteBackReg && in.valid && !inBits.lsuPayload.trap.isTrap
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
  csr.wEn := inBits.ctrl.wbuCtrl.isWriteBackCsr && 
    (imm.orR || !inBits.ctrl.wbuCtrl.isCsrWriteCheck) && in.valid
  csr.wAddr := inBits.lsuPayload.idu.csrAddr
  csr.wData := inBits.lsuPayload.exu.aluOut

  csr.pc := pc
  csr.isTrap := in.valid && inBits.lsuPayload.trap.isTrap
  csr.causeNum := inBits.lsuPayload.trap.cause

  PerfWhen("totalCyc", true.B, in.bits.ctrl.debugCtrl.get.isEbreak)
  PerfWhen("totalInst", out.valid, in.bits.ctrl.debugCtrl.get.isEbreak)
}
