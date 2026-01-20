package minirvcpu

import chisel3._
import chisel3.util._
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
  io.out := MuxLookup(io.in.aluOp, addResult)(Seq(
    add -> addResult
    ))
}

class EXUSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val aluResult = Output(UInt(cfg.xlen.W))
}

class EXU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val iduIn = Flipped(new IDUSignals)
    val regFileIn = Flipped(new RegisterFileSignals)
    val exuOut = new EXUSignals
  })

  val ctrlSig = io.iduIn.ctrlSignals.ex

  // 根据扩展实例化Alu
  val alus: ListMap[ExtTypeEnum.Type, AluParent] = cfg.extensions.collect {
    case ExtTypeEnum.I => ExtTypeEnum.I -> Module(new AluBase)
    case t => throw new IllegalArgumentException(s"Unsupported extension: $t")
  }.to(ListMap)
 
  // 连接Alu输入
  val src2 = MuxLookup(ctrlSig.aluSrc2, io.iduIn.imm)(Seq(
    AluSourceEnum.imm.asUInt -> io.iduIn.imm,
    AluSourceEnum.rs2.asUInt -> io.regFileIn.rData(1),
    ))

  val aluIn = Wire(Output(new AluInput))
  aluIn.aluOp := ctrlSig.aluOp
  aluIn.src1 := io.regFileIn.rData(0)
  aluIn.src2 := src2
  alus.foreach(alu => alu._2.io.in := aluIn) 

  // 根据扩展选择输出
  val muxSeq: Seq[(UInt, UInt)] = 
    alus.map { case (ext: ExtTypeEnum.Type, alu: AluParent) => ext.asUInt -> alu.io.out }.toSeq
  io.exuOut.aluResult := MuxLookup(ctrlSig.extType, muxSeq.head._2)(muxSeq)
}









