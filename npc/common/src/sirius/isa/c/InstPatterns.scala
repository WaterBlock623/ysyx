package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import org.chipsalliance.rvdecoderdb

case class InstPatternRvC(
)(
  implicit private val insts: Iterable[rvdecoderdb.Instruction],
  implicit private val cfg:   CoreConfig) {
  val pattern = Seq(
    InstPattern(
      "c.addi4spn",
      ExtTypeEnum.Zifencei,
      DontCare,
      isFlushIcache = true
    ),
  )
}
