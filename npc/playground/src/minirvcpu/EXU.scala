package minirvcpu

import chisel3._
import chisel3.util.MuxLookup
import scala.collection.immutable.ListMap

class AluInput(implicit private val cfg: CoreConfig) extends Bundle {
  val aluOp = Input(AluOpEnum())
  val src1 = Input(UInt(cfg.xlen.W))
  val src2 = Input(UInt(cfg.xlen.W))
}

class AluSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val in = new AluInput
  val out = Output(UInt(cfg.xlen.W))
}

class AluParent(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new AluSignals())
}

class AluBase(implicit private val cfg: CoreConfig) extends AluParent {
  val addResult = io.in.src1 + io.in.src2

  import AluOpEnum._
  io.out := MuxLookup(io.in.aluOp, 0.U)(Seq(
    add -> addResult
    ))
}

class EXU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val aluOp = Input(AluOpEnum())
    val extType = Input(ExtTypeEnum())
    val aluSrc2 = Input(AluSourceEnum())
    val rs1 = Input(UInt(cfg.xlen.W))
    val rs2 = Input(UInt(cfg.xlen.W))
    val imm = Input(UInt(cfg.xlen.W))
    val aluOut = Output(UInt(cfg.xlen.W))
  })

  // 根据扩展实例化Alu
  val alus: ListMap[ExtTypeEnum.Type, AluParent] = cfg.extensions.collect {
    case ExtTypeEnum.I => ExtTypeEnum.I -> Module(new AluBase)
    case t => throw new IllegalArgumentException(s"Unsupported extension: $t")
  }.to(ListMap)
 
  // 连接Alu输入
  val src2 = MuxLookup(io.aluSrc2, io.imm)(Seq(
    AluSourceEnum.imm -> io.imm,
    AluSourceEnum.rs2 -> io.rs2,
    ))
  val aluIn = Wire(Output(new AluInput))
  aluIn.aluOp := io.aluOp
  aluIn.src1 := io.rs1
  aluIn.src2 := src2
  alus.foreach(alu => alu._2.io.in := aluIn) 

  // 根据扩展选择输出
  val muxSeq: Seq[(ExtTypeEnum.Type, UInt)] = 
    alus.map { case (ext: ExtTypeEnum.Type, alu: AluParent) => ext -> alu.io.out }.toSeq
  io.aluOut := MuxLookup(io.extType, 0.U)(muxSeq)
}









