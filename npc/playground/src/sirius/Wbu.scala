package sirius

import chisel3._
import chisel3.util.MuxLookup
import chisel3.util.Decoupled
import rvspeccore.core.RVConfig

// 控制pc跳转和gpr读写
class Wbu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val exte = IO(new Bundle {
    val pcReg = new WbuToPcRegIO
    val regFlie = new WbuToRegFileIO
    val csr = new WbuToCsrIO
    val debugEbreak = Option.when(cfg.isDebug)(Input(Bool()))
  })
  val in = IO(Flipped(Decoupled(new LsuToWbuIO)))
  val debug = Option.when(cfg.isDebug)(IO(Output(new Bundle {
    val valid = Bool()
    val isJump = Bool()
    val jumpTarget = UInt(cfg.xlen.W)
  })))

  // DecoupledIO
  DecoupledFsm(false, in)
  in.ready := true.B
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
  pcReg.target := Mux(
    inBits.lsuPayload.trap.isTrap,
    exte.csr.mtvec,
    Mux(ctrl.isFromCsr, csrJumpTarget, normalJumpTarget)
  )
  pcReg.isJump := ctrl.isJump || ctrl.isFromCsr || (ctrl.isBranch && aluOut(
    0
  )) || inBits.lsuPayload.trap.isTrap
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
      WriteBackSelEnum.csr.asUInt -> csrData
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

  if (cfg.isDebug) {
    dontTouch(debug.get)
    debug.get.valid := in.valid
    debug.get.isJump := pcReg.wEn && pcReg.isJump
    debug.get.jumpTarget := pcReg.target
  }

  PerfWhen(
    "totalCyc",
    true.B,
    exte.debugEbreak
  )
  PerfWhen(
    "totalInst",
    in.valid,
    exte.debugEbreak
  )
}
