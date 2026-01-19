package common.minirvcpu

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import scala.collection.immutable.ListMap
import common.util.CanAutoGenSig
import playground.minirvcpu.{CoreConfig, ExtType, InstType}

trait HasMoreSignalInfo extends CanAutoGenSig {
  def extType: ExtType.Value
}

case class InstPatternMaker(
  name: String,
  bp: String,
  instType: InstType.InstType,
  isWriteBackReg: Boolean = false,
  ) extends DecodePattern {
    def bitPat: BitPat = BitPat("b" + bp)
}

// 指令定义
object InstPatterns {
  import InstType._
  val instsBase: Seq[InstPatternMaker] = Seq(
    InstPatternMaker("addi", "???????????? ????? 000 ????? 0010011", I, 
      isWriteBackReg = true),
    )
//  val instsExtM: Seq[InstPatternMaker] = Seq.empty
}

// 控制信号定义
object InstFields {
  val fieldsBase = Seq(
    new BoolDecodeField[InstPatternMaker] with HasMoreSignalInfo {
      def name = "isWriteBackReg"
      def extType = ExtType.I
      def stage = "wb"
      def genTable(i: InstPatternMaker) = if (i.isWriteBackReg) y else n
    }
  )
}

// 根据启用的扩展生成Seq[DecodePattern]和Seq[DecodeField]
case class InstDecodeCollector()(implicit private val cfg: CoreConfig) {
  private val patternMap = Map(
    ExtType.I -> InstPatterns.instsBase,
//    ExtType.M -> InstPatterns.instsExtM,
    )

  private val fieldMap = Map(
    ExtType.I -> InstFields.fieldsBase,
//    ExtType.M -> InstFields.fieldsExtM,
    )

  private def genSeq[T](m: Map[ExtType.Value, Seq[T]]): Seq[T] = {
    cfg.extensions.flatMap { key =>
      m.getOrElse(key, 
        throw new IllegalArgumentException(s"Unsupported extension: $key is not in $m"))
    }
  }

  val allPatterns: Seq[InstPatternMaker] = genSeq(patternMap)
  val allFields: Seq[DecodeField[InstPatternMaker, _ <: Data] with HasMoreSignalInfo] = 
    genSeq(fieldMap)
}

