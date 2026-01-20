package minirvcpu

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.MuxLookup
import chisel3.util.Fill

class ImmParser(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(cfg.xlen.W))
    val instType = Input(InstTypeEnum())
    val imm = Output(UInt(cfg.xlen.W))
  }) 

  if (cfg.extensions.contains(ExtTypeEnum.I)) {
    val inst = io.inst(32, 0)

    val immTypeI = Fill(cfg.xlen - 11, inst(31)) ## inst(30, 20)
    val immTypeS = Fill(cfg.xlen - 11, inst(31)) ## inst(30, 25) ## inst(11, 7)
    val immTypeB = 
      Fill(cfg.xlen - 12, inst(31)) ## inst(7) ## inst(30, 25) ## inst(11, 8) ## 0.U(1.W)
    val immTypeU = Fill(cfg.xlen - 31, inst(31)) ## inst(30, 12) ## 0.U(12.W)
    val immTypeJ = 
      Fill(cfg.xlen - 20, inst(31)) ## inst(19, 12) ## inst(20) ## inst(30, 21) ## 0.U(1.W)
    
    io.imm := MuxLookup(io.instType, immTypeI)(Seq(
      InstTypeEnum.I -> immTypeI,
      InstTypeEnum.S -> immTypeS,
      InstTypeEnum.B -> immTypeB,
      InstTypeEnum.U -> immTypeU,
      InstTypeEnum.J -> immTypeJ
      ))
  } else {
    throw new IllegalArgumentException("Unsupported extension")
  }
}

class InstDecoder(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(cfg.xlen.W))
    val ctrlSignals = Output(new CtrlSignals())
  })

  val decodeCollector = InstDecodeCollector()
  val decodeTable = new DecodeTable(decodeCollector.allPatterns, decodeCollector.allFields)
  val decodeResult = decodeTable.decode(io.inst)
  decodeCollector.allFields.foreach { f =>
    io.ctrlSignals.elements(f.stage).asInstanceOf[Record].elements(f.name) := 
      decodeResult(f.asInstanceOf[DecodeField[_, _ <: Data]])
  }
}

class IDU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(cfg.xlen.W))
    val ctrlSignals = Output(new CtrlSignals())
    val imm = Output(UInt(cfg.xlen.W))
  })
  
  val instDecoder = Module(new InstDecoder())
  val immParser = Module(new ImmParser())

  instDecoder.io.inst := io.inst
  io.ctrlSignals := instDecoder.io.ctrlSignals

  immParser.io.inst := io.inst
  immParser.io.instType := io.ctrlSignals
  
}
