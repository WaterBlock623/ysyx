package minirvcpu

import chisel3._

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

class PcRegister(implicit val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val wData = Input(UInt(cfg.xlen.W))
    val wEn = Input(Bool())
    val pc = Output(UInt(cfg.xlen.W))
  })

  val pcReg = RegInit(0.U(cfg.xlen.W))
  when (io.wEn) {
    pcReg := io.wData
  }
  io.pc := pcReg
}

class WBU(implicit private val cfg: CoreConfig) extends Module {
  
}
