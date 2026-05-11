package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import chisel3.util.experimental.BitSet
import org.chipsalliance.rvdecoderdb

trait DecodePatternBitSet extends DecodePattern {
  def bitSet:          BitSet
  // override def bitPat: BitPat = throw new IllegalAccessException("Should not access bitPat")
  override def bitPat: BitPat = BitPat.dontCare(bitSet.getWidth)
}

case class InstPattern(
  name:     String,
  extType:  ExtTypeEnum.Type,
  instType: Data,

  custom: Boolean = false,
  bs:     Option[BitSet] = None,

  aluIn1Sel: Data = DontCare,
  aluIn2Sel: Data = DontCare,
  aluOp:     Data = DontCare,
  exuOutSel: Data = DontCare,

  loadStoreType:   Data = DontCare,
  loadStoreLength: Data = DontCare,

  isWriteBackReg: Boolean = false,
  writeBackSel:   Data = DontCare,

  isBranch:      Boolean = false,
  isJump:        Boolean = false,
  isJumpCsr:     Boolean = false,
  jumpTargetSel: Data = DontCare,

  isWriteBackCsr:  Boolean = false,
  isCsrWriteCheck: Boolean = false,

  isFlushIcache: Boolean = false
)(
  implicit private val insts: Iterable[rvdecoderdb.Instruction],
  implicit private val cfg:   CoreConfig)
    extends DecodePatternBitSet {
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
  def bitSet: BitSet = {
    bs.getOrElse(BitPat("b" + inst.get.encoding.toString()))
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
