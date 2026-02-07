package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import org.chipsalliance.rvdecoderdb

case class InstPattern(
  name:     String,
  extType:  ExtTypeEnum.Type,
  instType: Data,

  custom: Boolean = false,
  bp:     Option[String] = None,

  aluIn2Sel: Data = DontCare,
  aluOp:     Data = DontCare,
  exuOutSel: Data = DontCare,

  loadStoreType:   Data = DontCare,
  loadStoreLength: Data = DontCare,

  // isWriteBackReg:  Boolean = false,
  writeBackSel: Data = DontCare,

  isBranch:      Boolean = false,
  isJump:        Boolean = false,
  jumpTargetSel: Data = DontCare
)(
  implicit private val insts: Iterable[rvdecoderdb.Instruction], 
  implicit private val cfg: CoreConfig)
    extends DecodePattern {
  val inst: Option[rvdecoderdb.Instruction] = {
    if (custom) {
      None
    } else {
      Some(
        insts
          .find(i => i.name == name)
          .getOrElse(
            throw new IllegalArgumentException(
              s"Can not find instruction: $name"
            )
          )
      )
    }
  }
  def bitPat: BitPat = {
    BitPat("b" + bp.getOrElse(("?" * (cfg.xlen - 32)) + inst.get.encoding.toString()))
  }

  def inArgs(field: String): Boolean = {
    this.inst.get.args.map(_.toString()).contains(field)
  }
}

case class InstPatterns(
)(
  implicit private val insts: Iterable[rvdecoderdb.Instruction], 
  implicit private val cfg: CoreConfig) {
  val patternBase = Seq(
    InstPattern(
      "add",
      ExtTypeEnum.I,
      InstTypeEnum.R,
      aluIn2Sel = AluInSelEnum.rs2,
      aluOp = AluOpEnum.add,
      exuOutSel = ExuOutSelEnum.aluBase,
      writeBackSel = WriteBackSelEnum.alu
    ),
    InstPattern(
      "addi",
      ExtTypeEnum.I,
      InstTypeEnum.I,
      aluIn2Sel = AluInSelEnum.imm,
      aluOp = AluOpEnum.add,
      exuOutSel = ExuOutSelEnum.aluBase,
      writeBackSel = WriteBackSelEnum.alu
    ),
    InstPattern(
      "lui",
      ExtTypeEnum.I,
      InstTypeEnum.U,
      writeBackSel = WriteBackSelEnum.imm
    ),
    InstPattern(
      "lw",
      ExtTypeEnum.I,
      InstTypeEnum.I,
      aluIn2Sel = AluInSelEnum.imm,
      aluOp = AluOpEnum.add,
      exuOutSel = ExuOutSelEnum.aluBase,
      loadStoreType = LoadStoreTypeEnum.signedLoad,
      loadStoreLength = LoadStoreLengthEnum.w,
      writeBackSel = WriteBackSelEnum.lsu
    ),
    InstPattern(
      "lbu",
      ExtTypeEnum.I,
      InstTypeEnum.I,
      aluIn2Sel = AluInSelEnum.imm,
      aluOp = AluOpEnum.add,
      exuOutSel = ExuOutSelEnum.aluBase,
      loadStoreType = LoadStoreTypeEnum.unsignedLoad,
      loadStoreLength = LoadStoreLengthEnum.b,
      writeBackSel = WriteBackSelEnum.lsu
    ),
    InstPattern(
      "sw",
      ExtTypeEnum.I,
      InstTypeEnum.S,
      aluIn2Sel = AluInSelEnum.imm,
      aluOp = AluOpEnum.add,
      exuOutSel = ExuOutSelEnum.aluBase,
      loadStoreType = LoadStoreTypeEnum.store,
      loadStoreLength = LoadStoreLengthEnum.w
    ),
    InstPattern(
      "sb",
      ExtTypeEnum.I,
      InstTypeEnum.S,
      aluIn2Sel = AluInSelEnum.imm,
      aluOp = AluOpEnum.add,
      exuOutSel = ExuOutSelEnum.aluBase,
      loadStoreType = LoadStoreTypeEnum.store,
      loadStoreLength = LoadStoreLengthEnum.b
    ),
    InstPattern(
      "jalr",
      ExtTypeEnum.I,
      InstTypeEnum.I,
      aluIn2Sel = AluInSelEnum.imm,
      aluOp = AluOpEnum.add,
      exuOutSel = ExuOutSelEnum.aluBase,
      writeBackSel = WriteBackSelEnum.staticNextPc,
      isJump = true,
      jumpTargetSel = JumpTargetSelEnum.alu
    ),
    InstPattern(
      "ebreak",
      ExtTypeEnum.I,
      DontCare
    )
  )

}
