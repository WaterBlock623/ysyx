package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import scala.collection.immutable.ListMap
import cpuutil.CanAutoGenSig
import org.chipsalliance.rvdecoderdb

object InstSetParser {
  def apply(name: String): (String, Int, String, String) = {
    val RVStrPattern = """rv(32|64)?_([a-z0-9]+)(?:_([a-z0-9]+))?""".r
    name match {
      case RVStrPattern(n, x, y) =>
        if (n != null && x != null && y == null) {
          ("nx", n.toInt, x, "")
        } else if (n == null && x != null && y == null) {
          ("x", 0, x, "")
        } else if (n == null && x != null && y != null) {
          ("xy", 0, x, y)
        } else {
          ("", 0, "", "")
        }
      case _ => ("", 0, "", "")
    }
  }
}

object MapToFlatSeq {
  def apply[T](
    cfg: CoreConfig,
    map: Map[(Set[ExtTypeEnum.Type], Set[Int]), Seq[T]]
  ): Seq[T] = {
    map
      .filter(m =>
        m._1._1.forall(t => cfg.extensions.contains(t)) && m._1._2
          .contains(cfg.xlen)
      )
      .flatMap(m => m._2)
      .toSeq
  }

}

// 根据启用的扩展生成Seq[DecodePattern]和Seq[DecodeField]
case class InstDecodeCollector(
)(
  implicit private val cfg: CoreConfig) {

  // 生成Fields
  private val fieldMap = Map(
    (Set(ExtTypeEnum.I), Set(32, 64)) -> InstFields.fieldsBase
//    Set(ExtTypeEnum.M) -> InstFields.fieldsExtM,
  )
  val allFields: Seq[DecodeField[InstPattern, _ <: Data] with CanAutoGenSig] =
    MapToFlatSeq(cfg, fieldMap)

  // 生成Patterns
  val allInsts = rvdecoderdb.instructions(cfg.rvOpcodesPath)
  val insts = allInsts.filter(inst => inst.pseudoFrom.isEmpty && inst.ratified)
  val instPatterns = InstPatterns()(insts, cfg)
  private val patternMap = Map(
    (Set(ExtTypeEnum.I), Set(32, 64)) -> instPatterns.patternBase
  )
  val allPatterns: Seq[InstPattern] = MapToFlatSeq(cfg, patternMap)
}
