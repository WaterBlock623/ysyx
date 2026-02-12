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
  val csrData = in.lsuPayload.idu.csrData

  // pc
  pcReg.target := in.lsuPayload.exu.jumpTarget
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
      WriteBackSelEnum.csr.asUInt -> csrData,
    )
  )

  // csr
  exte.csr.wEn := in.ctrl.wbuCtrl.isWriteBackCsr && !imm
  exte.csr.wAddr := in.lsuPayload.idu.csrAddr
  exte.csr.wData := in.lsuPayload.exu.aluOut
}
