package sirius

import chisel3._
import chisel3.util._
import scala.collection.immutable.ListMap
import chisel3.experimental.dataview._
import sirius.ExuOutSelEnum.aluBase

class AluIO(
  implicit private val cfg: CoreConfig)
    extends Bundle {
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

  // val useSub = Seq(sub, eql, neq, lt, ltu, ge, geu, clear)
  //   .map(io.aluOp === _.asUInt)
  //   .reduce(_ || _)
  // val xorSrc2 = io.src2 ^ Fill(io.src2.getWidth, useSub)
  // val addSub = io.src1 +& xorSrc2 + useSub.asUInt
  // val addSubResult = addSub.tail(1)
  // val addResult = addSubResult
  // val subResult = addSubResult
  // val eqlResult = addSubResult === 0.U
  // val overflow = io.src1.head(1) === xorSrc2.head(1) && io.src1.head(1) =/= addSubResult.head(1)
  // val ltResult = addSubResult.head(1) ^ overflow
  // val geResult = !ltResult
  // val geuResult = addSub.head(1)
  // val ltuResult = !geuResult
  // val andClearResult = io.src1 & xorSrc2
  // val andResult = andClearResult
  // val clearResult = andClearResult

  val addResult = io.src1 + io.src2
  val subResult = io.src1 - io.src2
  val eqlResult = io.src1 === io.src2
  val neqResult = !eqlResult
  val ltResult = io.src1.asSInt < io.src2.asSInt
  val ltuResult = io.src1 < io.src2
  val geResult = !ltResult
  val geuResult = !ltuResult
  val andResult = io.src1 & io.src2
  val orResult = io.src1 | io.src2
  val xorResult = io.src1 ^ io.src2
  val shiftNum = io.src2(log2Ceil(cfg.xlen) - 1, 0)
  val sllResult = io.src1 << shiftNum
  val srlResult = io.src1 >> shiftNum
  val sraResult = (io.src1.asSInt >> shiftNum).asUInt
  val direct1Result = io.src1
  val clearResult = io.src1 & ~io.src2

  io.out := addResult
  switch(io.aluOp) {
    // is(add.asUInt) {io.out := addResult; assert(io.out === (io.src1 + io.src2))}
    // is(sub.asUInt) {io.out := subResult; assert(io.out === (io.src1 - io.src2))}
    // is(eql.asUInt) {io.out := eqlResult; assert(io.out === (io.src1 === io.src2))}
    // is(neq.asUInt) {io.out := neqResult; assert(io.out === (io.src1 =/= io.src2))}
    // is(lt.asUInt) {io.out := ltResult; assert(io.out === (io.src1.asSInt < io.src2.asSInt))}
    // is(ltu.asUInt) {io.out := ltuResult; assert(io.out === (io.src1 < io.src2))}
    // is(ge.asUInt) {io.out := geResult; assert(io.out === (io.src1.asSInt >= io.src2.asSInt))}
    // is(geu.asUInt) {io.out := geuResult; assert(io.out === (io.src1 >= io.src2))}
    // is(and.asUInt) {io.out := andResult; assert(io.out === (io.src1 & io.src2))}
    is(add.asUInt) {io.out := addResult}
    is(sub.asUInt) {io.out := subResult}
    is(eql.asUInt) {io.out := eqlResult}
    is(neq.asUInt) {io.out := neqResult}
    is(lt.asUInt) {io.out := ltResult}
    is(ltu.asUInt) {io.out := ltuResult}
    is(ge.asUInt) {io.out := geResult}
    is(geu.asUInt) {io.out := geuResult}
    is(and.asUInt) {io.out := andResult}
    is(or.asUInt) {io.out := orResult}
    is(xor.asUInt) {io.out := xorResult}
    is(sll.asUInt) {io.out := sllResult}
    is(srl.asUInt) {io.out := srlResult}
    is(sra.asUInt) {io.out := sraResult}
    is(direct1.asUInt) {io.out := direct1Result}
    // is(clear.asUInt) {io.out := clearResult; assert(io.out === (io.src1 & ~io.src2))}
    is(clear.asUInt) {io.out := clearResult}
  }
  // io.out := MuxLookup(io.aluOp, addResult)(
  //   Seq(
  //     add.asUInt -> addResult,
  //     sub.asUInt -> subResult,
  //     eql.asUInt -> eqlResult,
  //     neq.asUInt -> neqResult,
  //     lt.asUInt -> ltResult,
  //     ltu.asUInt -> ltuResult,
  //     ge.asUInt -> geResult,
  //     geu.asUInt -> geuResult,
  //     and.asUInt -> andResult,
  //     or.asUInt -> orResult,
  //     xor.asUInt -> xorResult,
  //     sll.asUInt -> sllResult,
  //     srl.asUInt -> srlResult,
  //     sra.asUInt -> sraResult,
  //     direct1.asUInt -> direct1Result,
  //     clear.asUInt -> clearResult,
  //   )
  // )
}

class JumpTargetGenerator(
  implicit private val cfg: CoreConfig)
    extends Module {
  val io = IO(new Bundle {
    val jumpTargetSel = Input(UInt(JumpTargetSelEnum.getWidth.W))
    val pc = Input(UInt(cfg.xlen.W))
    val imm = Input(UInt(cfg.xlen.W))
    val aluResult = Input(UInt(cfg.xlen.W))
    val jumpTarget = Output(UInt(cfg.xlen.W))
  })

  val pcPlusImm = (io.pc + io.imm) & ~1.U(cfg.xlen.W)
  io.jumpTarget := MuxLookup(io.jumpTargetSel, pcPlusImm)(
    Seq(
      JumpTargetSelEnum.pcPlusImm.asUInt -> pcPlusImm,
      JumpTargetSelEnum.alu.asUInt -> io.aluResult
    )
  )
}

class Exu(
  implicit private val cfg:  CoreConfig,
  implicit private val ucfg: UnitConfig)
    extends Module {
  val exte = IO(new Bundle {
    val csr = new ExuToCsrIO
  })
  val in = IO(Flipped(Decoupled(new IduToExuIO)))
  val out = IO(Decoupled(new ExuToLsuIO))

  // DecoupledIO
  DecoupledMasterSlaveFsm(out, in)
  in.ready := out.ready
  out.valid := in.valid
  val inBits = in.bits
  val outBits = out.bits

  outBits.exuPayload.viewAsSupertype(new IduPayload) := inBits.iduPayload
  outBits.ctrl := inBits.ctrl.viewAsSupertype(new LsuCtrl)

  val ctrl = inBits.ctrl.exuCtrl
  val imm = inBits.iduPayload.idu.imm
  val rs1Data = inBits.iduPayload.idu.rs1Data
  val rs2Data = inBits.iduPayload.idu.rs2Data

  // csr
  exte.csr.rAddr := inBits.iduPayload.idu.csrAddr
  val csrData = exte.csr.rData
  outBits.exuPayload.exu.csrData := csrData

  // 根据扩展实例化Alu
  // val alus: ListMap[ExtTypeEnum.Type, AluParent] = cfg.extensions().collect {
  //   case ExtTypeEnum.I => ExtTypeEnum.I -> Module(new AluBase)
  //   case t => throw new IllegalArgumentException(s"Unsupported extension: $t")
  // }.to(ListMap)
  val alus: ListMap[ExuOutSelEnum.Type, AluParent] = ucfg.aluMap().flatten.map {
    case (outSel: ExuOutSelEnum.Type, alu: (() => AluParent)) =>
      (outSel -> Module(alu()))
  }

  // 连接Alu输入
  val src1 = MuxLookup(ctrl.aluIn1Sel, rs1Data)(
    Seq(
      // AluInSelEnum.imm.asUInt -> imm,
      AluInSelEnum.rs.asUInt -> rs1Data,
      AluInSelEnum.pc.asUInt -> inBits.iduPayload.ifu.pc
    )
  )
  val src2 = MuxLookup(ctrl.aluIn2Sel, imm)(
    Seq(
      AluInSelEnum.imm.asUInt -> imm,
      AluInSelEnum.rs.asUInt -> rs2Data,
      AluInSelEnum.csr.asUInt -> csrData
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
  val aluOut = MuxLookup(ctrl.exuOutSel, outTable.head._2)(
    outTable
  )
  outBits.exuPayload.exu.aluOut := aluOut

  // 计算跳转地址
  val jumpTargetGenerator = Module(new JumpTargetGenerator)
  jumpTargetGenerator.io.jumpTargetSel := inBits.ctrl.wbuCtrl.jumpTargetSel
  jumpTargetGenerator.io.pc := inBits.iduPayload.ifu.pc
  jumpTargetGenerator.io.imm := imm
  jumpTargetGenerator.io.aluResult := aluOut
  outBits.exuPayload.exu.jumpTarget := jumpTargetGenerator.io.jumpTarget

  PerfWhen(
    "calcFinish",
    out.fire,
    in.valid && in.bits.ctrl.debugCtrl.map(_.isEbreak).getOrElse(false.B)
  )
}
