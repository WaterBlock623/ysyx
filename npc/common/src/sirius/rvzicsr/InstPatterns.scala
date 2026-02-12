package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import org.chipsalliance.rvdecoderdb

case class InstPatternRvZicsr(
)(
  implicit private val insts: Iterable[rvdecoderdb.Instruction],
  implicit private val cfg:   CoreConfig) {
  val pattern = Seq(
    InstPattern(
      "csrrs",
      ExtTypeEnum.Zicsr,
      InstTypeEnum.I,
      aluIn1Sel = AluInSelEnum.rs,
      aluIn2Sel = AluInSelEnum.csr,
      aluOp = AluOpEnum.or,
      exuOutSel = ExuOutSelEnum.aluBase,
      isWriteBackReg = true,
      writeBackSel = WriteBackSelEnum.csr,
      isWriteBackCsr = true,
    ),
  )
}
