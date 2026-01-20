package minirvcpu

import chisel3._
import chisel3.util.MuxLookup

class MemSignalsTemplate(private val rPortNum: Int, private val wPortNum: Int
  , private val addrWidth: Int, private val dataWidth: Int) extends Bundle {
    val rAddr = Input(Vec(rPortNum, UInt(addrWidth.W)))
    val rData = Output(Vec(rPortNum, UInt(dataWidth.W)))
    val wAddr = Input(Vec(wPortNum, UInt(addrWidth.W)))
    val wData = Input(Vec(wPortNum, UInt(dataWidth.W)))
    val wEn = Input(Bool())
}

class RegFileSignals(implicit val cfg: CoreConfig) 
  extends MemSignalsTemplate(rPortNum = 2,
                             wPortNum = 1,
                             addrWidth = cfg.registerAddrWidth,
                             dataWidth = cfg.xlen)

class RegisterFile(implicit val cfg: CoreConfig) extends Module {
  val io = IO(new RegFileSignals)

  val regFile = Reg(Vec(cfg.registerNum, UInt(cfg.xlen.W)))
  when (io.wEn) {
    regFile(io.wAddr(0)) := io.wData(0)
  }
  regFile(0) := 0.U
  io.rData(0) := regFile(io.rAddr(0))
  io.rData(1) := regFile(io.rAddr(1))
}

class PcRegSignals(implicit private val cfg: CoreConfig) extends Bundle {
    val isBrach = Input(Bool())
    val imm = Input(UInt(cfg.xlen.W))
    val aluResult = Input(UInt(cfg.xlen.W))
    val branchValSrc = Input(BranchValSrcEnum())
    val pc = Output(UInt(cfg.xlen.W))
}

class PcRegister(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new PcRegSignals)

  val branchVal = MuxLookup(io.branchValSrc, io.imm)(Seq(
    BranchValSrcEnum.imm -> io.imm,
    BranchValSrcEnum.alu -> io.aluResult
    ))
  val isWriteBranchVal = io.isBrach && io.aluResult(0)
  val pcReg = RegInit(0.U(cfg.xlen.W))
  val pcNext = Mux(isWriteBranchVal, branchVal, pcReg + 4.U)
  pcReg := pcNext
  io.pc := pcReg
}

class WBU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val pcRegSignals = new PcRegSignals
    val regFileSignals = new RegFileSignals
  }) 

  val pcRegister = Module(new PcRegister)
  val regFile = Module(new RegisterFile)
  io.pcRegSignals :<>= pcRegister.io
  io.regFileSignals :<>= regFile.io
}
