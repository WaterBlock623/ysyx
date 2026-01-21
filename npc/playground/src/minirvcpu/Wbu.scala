package minirvcpu

import chisel3._
import chisel3.util.MuxLookup

// gpr
class RegisterFileSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val rData = Output(Vec(2, UInt(cfg.xlen.W)))
}

class RegisterFile(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val wbuIn = Flipped(new WbuSignals)
    val registerFileOut = new RegisterFileSignals
  })

  val sigIn = io.wbuIn.registerFile
  val regFile = Reg(Vec(cfg.registerNum, UInt(cfg.xlen.W)))
  when (sigIn.wEn) {
    regFile(sigIn.wAddr) := sigIn.wData
  }
  regFile(0) := 0.U
  io.registerFileOut.rData(0) := regFile(sigIn.rAddr(0))
  io.registerFileOut.rData(1) := regFile(sigIn.rAddr(1))
}

// pc
class PcRegisterSignals(implicit private val cfg: CoreConfig) extends Bundle {
    val pc = Output(UInt(cfg.xlen.W))
}

class PcRegister(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val wbuIn = Flipped(new WbuSignals)
    val pcRegisterOut = new PcRegisterSignals
  })

  val sigIn = io.wbuIn.pcRegister
  val pcReg = RegInit(0.U(cfg.xlen.W))
  val pcNext = Mux(sigIn.isJump, sigIn.jumpAddr, pcReg + 4.U)
  pcReg := pcNext
  io.pcRegisterOut.pc := pcReg
}

// 控制pc跳转和gpr读写
class WbuSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val pcRegister = new Bundle {
    val jumpAddr = UInt(cfg.xlen.W)
    val isJump = Bool()
  }
  val registerFile = new Bundle {
    val rAddr = Vec(2, UInt(cfg.registerAddrWidth.W))
    val wAddr = UInt(cfg.registerAddrWidth.W)
    val wData = UInt(cfg.xlen.W)
    val wEn = Bool()
  }
}

class Wbu(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val iduIn = Flipped(new IduSignals)
    val exuIn = Flipped(new ExuSignals)
    val pcRegisterIn = Flipped(new PcRegisterSignals)
    val wbuOut = new WbuSignals
  }) 


  val ctrlSig = io.iduIn.ctrlSignals.wb
  val pcRegOut = io.wbuOut.pcRegister
  val regFileOut = io.wbuOut.registerFile

  // pc
  pcRegOut.jumpAddr := MuxLookup(ctrlSig.jumpAddrSel, io.iduIn.imm)(Seq(
      JumpAddrSelEnum.imm.asUInt -> io.iduIn.imm,
      JumpAddrSelEnum.alu.asUInt -> io.exuIn.aluResult
    ))
  pcRegOut.isJump := ctrlSig.isJump || (ctrlSig.isBranch && io.exuIn.aluResult(0))

  // gpr
  regFileOut.rAddr := io.iduIn.regFileRAddr
  regFileOut.wAddr := io.iduIn.regFileWAddr
  regFileOut.wEn := ctrlSig.isWriteBackReg
  regFileOut.wData := MuxLookup(ctrlSig.writeBackSel, io.exuIn.aluResult)(Seq(
    WriteBackSelEnum.alu.asUInt -> io.exuIn.aluResult,
    WriteBackSelEnum.staticNextPc.asUInt -> (io.pcRegisterIn.pc + 4.U)
    ))
}
