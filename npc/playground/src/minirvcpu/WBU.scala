package minirvcpu

import chisel3._
import chisel3.util.MuxLookup

class RegisterFileSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val rData = Output(Vec(2, UInt(cfg.xlen.W)))
}

class RegisterFile(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val wbuIn = Flipped(new WBUSignals)
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

class PcRegisterSignals(implicit private val cfg: CoreConfig) extends Bundle {
    val pc = Output(UInt(cfg.xlen.W))
}

class PcRegister(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val wbuIn = Flipped(new WBUSignals)
    val pcRegisterOut = new PcRegisterSignals
  })

  val sigIn = io.wbuIn.pcRegister
  val pcReg = RegInit(0.U(cfg.xlen.W))
  val pcNext = Mux(sigIn.isWriteBranchVal, sigIn.branchVal, pcReg + 4.U)
  pcReg := pcNext
  io.pcRegisterOut.pc := pcReg
}

class WBUSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val pcRegister = new Bundle {
    val branchVal = UInt(cfg.xlen.W)
    val isWriteBranchVal = Bool()
  }
  val registerFile = new Bundle {
    val rAddr = UInt(cfg.registerAddrWidth.W)
    val wAddr = UInt(cfg.registerAddrWidth.W)
    val wData = UInt(cfg.xlen.W)
    val wEn = Bool()
  }
}

class WBU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val iduIn = Flipped(new IDUSignals)
    val exuIn = Flipped(new EXUSignals)
    val wbuOut = new WBUSignals
  }) 


  val ctrlSig = io.iduIn.ctrlSignals.wb
  val pcRegOut = io.wbuOut.pcRegister
  val regFileOut = io.wbuOut.registerFile

  pcRegOut.branchVal := MuxLookup(ctrlSig.branchValSrc, io.iduIn.imm)(Seq(
      BranchValSrcEnum.imm.asUInt -> io.iduIn.imm,
      BranchValSrcEnum.alu.asUInt -> io.exuIn.aluResult
    ))

  pcRegOut.isWriteBranchVal := ctrlSig.isBranch && io.exuIn.aluResult(0)

  regFileOut.rAddr := io.iduIn.regFileRAddr
  regFileOut.wAddr := io.iduIn.regFileWAddr
  regFileOut.wData := io.exuIn.aluResult
  regFileOut.wEn := ctrlSig.isWriteBackReg
}
