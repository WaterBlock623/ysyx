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

  aluIn1Sel: Data = DontCare,
  aluIn2Sel: Data = DontCare,
  aluOp:     Data = DontCare,
  exuOutSel: Data = DontCare,

  loadStoreType:   Data = DontCare,
  loadStoreLength: Data = DontCare,

  isWriteBackReg:  Boolean = false,
  writeBackSel: Data = DontCare,

  isBranch:      Boolean = false,
  isJump:        Boolean = false,
  isFromCsr:        Boolean = false,
  jumpTargetSel: Data = DontCare,

  isWriteBackCsr: Boolean = false,
  isCsrWriteCheck: Boolean = false,

  isFlushIcache: Boolean = false,
)(
  implicit private val insts: Iterable[rvdecoderdb.Instruction],
  implicit private val cfg:   CoreConfig)
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
    BitPat(
      "b" + bp.getOrElse(("?" * (cfg.xlen - 32)) + inst.get.encoding.toString())
    )
  }

  def inArgs(field: String): Boolean = {
    this.inst.get.args.map(_.toString()).contains(field)
  }
}


case class InstPatterns(
)(
  implicit private val insts: Iterable[rvdecoderdb.Instruction],
  implicit private val cfg:   CoreConfig) {
  val patternRvI = InstPatternRvI().pattern
  val patternRvZicsr = InstPatternRvZicsr().pattern
  val patternRvZifencei = InstPatternRvZifencei().pattern
}
