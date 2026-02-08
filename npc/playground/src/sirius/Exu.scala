package sirius

import chisel3._
import chisel3.util._
import scala.collection.immutable.ListMap
import chisel3.experimental.dataview._
import sirius.ExuOutSelEnum.aluBase

class AluIO(implicit private val cfg: CoreConfig) extends Bundle {
  val src1 = Output(UInt(cfg.xlen.W))
  val src2 = Output(UInt(cfg.xlen.W))
  val aluOp = Output(UInt(AluOpEnum.getWidth.W))
  val out = Input(UInt(cfg.xlen.W))
}

// Alu父类
class AluParent(
  implicit private val cfg: CoreConfig)
    extends Module {
  val io = IO(Flipped(new AluIO()))
}

// 主Alu
class AluBase(
  implicit private val cfg: CoreConfig)
    extends AluParent {
  import AluOpEnum._

  // val isSub = io.aluOp === sub.asUInt
  // val negSrc2 = ~io.src2 + 1.U
  // val addSubResult = io.src1 + Mux(isSub, negSrc2, io.src2)
  //
  // io.out := MuxLookup(io.aluOp, addSubResult)(
  //   Seq(
  //     add.asUInt -> addSubResult,
  //     sub.asUInt -> addSubResult,
  //   )
  // )
  
  val addResult = io.src1 + io.src2
  val subResult = io.src1 - io.src2
  // val andResult = io.src1 & io.src2
  // val orResult = io.src1 | io.src2
  // val xorResult = io.src1 ^ io.src2

  io.out := MuxLookup(io.aluOp, addResult)(
    Seq(
      add.asUInt -> addResult,
      sub.asUInt -> subResult,
      and.asUInt -> andResult,
      or.asUInt -> orResult,
      xor.asUInt -> xorResult,
    )
  )
}

class Exu(implicit private val cfg: CoreConfig, 
  implicit private val ucfg: UnitConfig) extends Module {
  val in = IO(Flipped(new IduToExuIO))
  val out = IO(new ExuToLsuIO)

  out.exuPayload.viewAsSupertype(new IduPayload) := in.iduPayload
  out.ctrl := in.ctrl.viewAsSupertype(new LsuCtrl)

  val ctrl = in.ctrl.exuCtrl
  val imm = in.iduPayload.idu.imm
  val rs1Data = in.iduPayload.idu.rs1Data
  val rs2Data = in.iduPayload.idu.rs2Data

  // 根据扩展实例化Alu
  // val alus: ListMap[ExtTypeEnum.Type, AluParent] = cfg.extensions().collect {
  //   case ExtTypeEnum.I => ExtTypeEnum.I -> Module(new AluBase)
  //   case t => throw new IllegalArgumentException(s"Unsupported extension: $t")
  // }.to(ListMap)
  val alus: ListMap[ExuOutSelEnum.Type, AluParent] = ucfg.aluMap().flatten.map {
    case (outSel: ExuOutSelEnum.Type, alu: (() => AluParent)) => (outSel -> Module(alu()))
  }

  // 连接Alu输入
  val src1 = MuxLookup(ctrl.aluIn1Sel, rs1Data)(
    Seq(
      // AluInSelEnum.imm.asUInt -> imm,
      AluInSelEnum.rs.asUInt -> rs1Data,
      AluInSelEnum.pc.asUInt -> in.iduPayload.ifu.pc,
    )
  )
  val src2 = MuxLookup(ctrl.aluIn2Sel, imm)(
    Seq(
      AluInSelEnum.imm.asUInt -> imm,
      AluInSelEnum.rs.asUInt -> rs2Data,
    )
  )
  val aluIn = Wire(new AluIO)
  aluIn.out := DontCare
  aluIn.aluOp := ctrl.aluOp
  aluIn.src1 := src1
  aluIn.src2 := src2
  alus.foreach(alu => alu._2.io :<= aluIn)

  // 根据扩展选择输出
  val outTable: Seq[(UInt, UInt)] =
    alus.map { case (outSel: ExuOutSelEnum.Type, alu: AluParent) =>
      outSel.asUInt -> alu.io.out
    }.toSeq
  out.exuPayload.exu.aluOut := MuxLookup(ctrl.exuOutSel, outTable.head._2)(
    outTable
  )
}
