package minirvcpu

import chisel3._
import chisel3.util.MuxLookup
import scala.collection.immutable.ListMap

class AluSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val aluOp = Input(AluOpEnum())
  val src1 = Input(UInt(cfg.xlen.W))
  val src2 = Input(UInt(cfg.xlen.W))
  val out = Output(UInt(cfg.xlen.W))
}

class AluBase(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new AluSignals())
  
  val addResult = io.src1 + io.src2

  import AluOpEnum._
  io.out := MuxLookup(io.aluOp, 0.U)(Seq(
    add -> addResult
    ))
}

class EXU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val instType = Input(InstTypeEnum())
    val imm = Input(UInt(cfg.xlen.W))
    val rs1 = Input(UInt(cfg.xlen.W))
    val rs2 = Input(UInt(cfg.xlen.W))
    val aluOut = Output(UInt(cfg.xlen.W))
  })

  val alus: ListMap[ExtTypeEnum.Type, _ <: Module] = cfg.extensions.collect {
    case ExtTypeEnum.I => ExtTypeEnum.I -> new AluBase
    case t => throw new IllegalArgumentException(s"Unsupported extension: $t")
  }.to(ListMap)
  
  
}
