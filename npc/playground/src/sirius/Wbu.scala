package sirius

import chisel3._
import chisel3.util.MuxLookup

// gpr
class RegisterFile(
  implicit private val cfg: CoreConfig)
    extends Module {
  val io = IO(new Bundle {
    val wbuIn = Flipped(new WbuSignals)
    val registerFileOut = new RegisterFileSignals
  })

  val sigIn = io.wbuIn.registerFile
  val regFile = Reg(Vec(cfg.registerNum, UInt(cfg.xlen.W)))
  when(sigIn.wEn) {
    regFile(sigIn.wAddr) := sigIn.wData
  }
  regFile(0) := 0.U
  io.registerFileOut.rData(0) := regFile(sigIn.rAddr(0))
  io.registerFileOut.rData(1) := regFile(sigIn.rAddr(1))
}

// pc
class PcReg(
  implicit private val cfg: CoreConfig)
    extends Module {
  val io = IO(new Bundle {
    val wbuIn = Flipped(new WbuSignals)
    val pcRegisterOut = new PcRegisterSignals
  })

  val sigIn = io.wbuIn.pcRegister
  val pcReg = RegInit("h80000000".U(cfg.xlen.W))
  val pcNext = Mux(sigIn.isJump, sigIn.target, pcReg + 4.U)
  pcReg := pcNext
  io.pcRegisterOut.pc := pcReg
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

  // pc
  pcReg.target := MuxLookup(ctrl.jumpTargetSel, io.iduIn.imm)(
    Seq(
      JumpTargetSelEnum.imm.asUInt -> io.iduIn.imm,
      JumpTargetSelEnum.alu.asUInt -> io.exuIn.aluResult
    )
  )
  pcRegOut.isJump := ctrlSig.isJump || (ctrlSig.isBranch && io.exuIn.aluResult(
    0
  ))

  // gpr
  regFileOut.rAddr := io.iduIn.regFileRAddr
  regFileOut.wAddr := io.iduIn.regFileWAddr
  regFileOut.wEn := ctrlSig.isWriteBackReg
  regFileOut.wData := MuxLookup(ctrlSig.writeBackSel, io.exuIn.aluResult)(
    Seq(
      WriteBackSelEnum.alu.asUInt -> io.exuIn.aluResult,
      WriteBackSelEnum.imm.asUInt -> io.iduIn.imm,
      WriteBackSelEnum.staticNextPc.asUInt -> (io.pcRegisterIn.pc + 4.U),
      WriteBackSelEnum.lsu.asUInt -> io.lsuIn.loadData,
    )
  )
}
