package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import org.chipsalliance.rvdecoderdb

case class InstPatternRvI(
)(
  implicit private val insts: Iterable[rvdecoderdb.Instruction],
  implicit private val cfg:   CoreConfig) {
  val pattern = Seq(
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
