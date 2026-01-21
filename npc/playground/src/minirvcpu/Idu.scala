package minirvcpu

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.MuxLookup
import chisel3.util.Fill

class ImmParser(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(cfg.xlen.W))
    val instType = Input(UInt(InstTypeEnum.getWidth.W))
    val imm = Output(UInt(cfg.xlen.W))
  }) 

  if (cfg.extensions.contains(ExtTypeEnum.I)) {
    val inst = io.inst(31, 0)

    val immTypeI = Fill(cfg.xlen - 11, inst(31)) ## inst(30, 20)
    val immTypeS = Fill(cfg.xlen - 11, inst(31)) ## inst(30, 25) ## inst(11, 7)
    val immTypeB = 
      Fill(cfg.xlen - 12, inst(31)) ## inst(7) ## inst(30, 25) ## inst(11, 8) ## 0.U(1.W)
    val immTypeU = Fill(cfg.xlen - 31, inst(31)) ## inst(30, 12) ## 0.U(12.W)
    val immTypeJ = 
      Fill(cfg.xlen - 20, inst(31)) ## inst(19, 12) ## inst(20) ## inst(30, 21) ## 0.U(1.W)
    
    io.imm := MuxLookup(io.instType, immTypeI)(Seq(
      InstTypeEnum.I.asUInt -> immTypeI,
      InstTypeEnum.S.asUInt -> immTypeS,
      InstTypeEnum.B.asUInt -> immTypeB,
      InstTypeEnum.U.asUInt -> immTypeU,
      InstTypeEnum.J.asUInt -> immTypeJ
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

class IduSignals(implicit private val cfg: CoreConfig) extends Bundle {
    val ctrlSignals = Output(new CtrlSignals())
    val imm = Output(UInt(cfg.xlen.W))
    val regFileRAddr = Output(Vec(2, UInt(cfg.registerAddrWidth.W)))
    val regFileWAddr = Output(UInt(cfg.registerAddrWidth.W))
}

class Idu(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val ifuIn = Flipped(new IfuSignals)
    val iduOut = new IduSignals
  })
  
  io.iduOut.regFileRAddr(0) := io.ifuIn.inst(19, 15)
  io.iduOut.regFileRAddr(1) := io.ifuIn.inst(24, 20)

  io.iduOut.regFileWAddr := io.ifuIn.inst(11, 7)

  val instDecoder = Module(new InstDecoder())
  val immParser = Module(new ImmParser())

  instDecoder.io.inst := io.ifuIn.inst
  io.iduOut.ctrlSignals := instDecoder.io.ctrlSignals

  immParser.io.inst := io.ifuIn.inst
  immParser.io.instType := io.iduOut.ctrlSignals.id.instType
  io.iduOut.imm := immParser.io.imm 
}
