package minirvcpu

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import scala.collection.immutable.ListMap
import cpuutil.CanAutoGenSig

trait HasMoreSignalInfo extends CanAutoGenSig {
  def extType: ExtTypeEnum.Type
}

object ExtTypeEnum extends ChiselEnum {
  val I, M = Value
}

object InstTypeEnum extends ChiselEnum {
  val I = Value
}

object AluSourceEnum extends ChiselEnum {
  val imm, rd2 = Value
}

object AluOpEnum extends ChiselEnum {
  val add, sub = Value
}

case class InstPatternMaker(
  name: String,
  bp: String,
  instType: InstTypeEnum.Type,
  isWriteBackReg: Boolean = false,
  aluSrc2: Data = DontCare,
  aluOp: Data = DontCare,
  ) extends DecodePattern {
    def bitPat: BitPat = BitPat("b" + bp)
}

// 指令定义
object InstPatterns {
  import InstTypeEnum._
  import AluSourceEnum._
  import AluOpEnum._

  val instsBase: Seq[InstPatternMaker] = Seq(
    InstPatternMaker("addi", "???????????? ????? 000 ????? 0010011", I, 
      isWriteBackReg = true, aluSrc2 = imm, aluOp = add),
    )
//  val instsExtM: Seq[InstPatternMaker] = Seq.empty
}

// 控制信号定义
object InstFields {
  val fieldsBase = Seq(
    new BoolDecodeField[InstPatternMaker] with HasMoreSignalInfo {
      def name = "isWriteBackReg"
      def extType = ExtTypeEnum.I
      def stage = "wb"
      def genTable(i: InstPatternMaker) = if (i.isWriteBackReg) y else n
    },
    
    new DecodeField[InstPatternMaker, UInt] with HasMoreSignalInfo {
      def name = "aluSrc2"
      def extType = ExtTypeEnum.I
      def stage = "ex"
      def chiselType = UInt(AluSourceEnum.getWidth.W)
      def genTable(i: InstPatternMaker) = {
        i.aluSrc2 match {
          case e: AluSourceEnum.Type => BitPat(e.litValue.U(AluSourceEnum.getWidth.W))
          case DontCare => dc
          // case DontCare => throw new IllegalArgumentException(s"dc aluSrc2 value")
          case v => throw new IllegalArgumentException(s"Invalid aluSrc2 value: $v")
        } 
      }
    },

    new DecodeField[InstPatternMaker, UInt] with HasMoreSignalInfo {
      def name = "aluOp"
      def extType = ExtTypeEnum.I
      def stage = "ex"
      def chiselType = UInt(AluOpEnum.getWidth.W)
      def genTable(i: InstPatternMaker) = {
        i.aluOp match {
          case e: AluOpEnum.Type => BitPat(e.litValue.U(AluOpEnum.getWidth.W))
          case DontCare => dc
          // case DontCare => throw new IllegalArgumentException(s"dc aluOp value")
          case v => throw new IllegalArgumentException(s"Invalid aluOp value: $v")
        } 
      }
    },

    new DecodeField[InstPatternMaker, UInt] with HasMoreSignalInfo {
      def name = "extTypeEnum"
      def extType = ExtTypeEnum.I
      def stage = "ex"
      def chiselType = UInt(ExtTypeEnum.getWidth.W)
      def genTable(i: InstPatternMaker) = {
        i.aluOp match {
          case e: InstTypeEnum.Type => BitPat(e.litValue.U(ExtTypeEnum.getWidth.W))
          case v => throw new IllegalArgumentException(s"Invalid aluOp value: $v")
        } 
      }
    },

    new DecodeField[InstPatternMaker, UInt] with HasMoreSignalInfo {
      def name = "instTypeEnum"
      def extType = ExtTypeEnum.I
      def stage = "id"
      def chiselType = UInt(InstTypeEnum.getWidth.W)
      def genTable(i: InstPatternMaker) = {
        i.aluOp match {
          case e: InstTypeEnum.Type => BitPat(e.litValue.U(InstTypeEnum.getWidth.W))
          case v => throw new IllegalArgumentException(s"Invalid aluOp value: $v")
        } 
      }
    },
  )
}

// 根据启用的扩展生成Seq[DecodePattern]和Seq[DecodeField]
case class InstDecodeCollector()(implicit private val cfg: CoreConfig) {
  private val patternMap = Map(
    ExtTypeEnum.I -> InstPatterns.instsBase,
//    ExtType.M -> InstPatterns.instsExtM,
    )

  private val fieldMap = Map(
    ExtTypeEnum.I -> InstFields.fieldsBase,
//    ExtType.M -> InstFields.fieldsExtM,
    )

  private def genSeq[T](m: Map[ExtTypeEnum.Type, Seq[T]]): Seq[T] = {
    cfg.extensions.flatMap { key =>
      m.getOrElse(key, 
        throw new IllegalArgumentException(s"Unsupported extension: $key is not in $m"))
    }
  }

  val allPatterns: Seq[InstPatternMaker] = genSeq(patternMap)
  val allFields: Seq[DecodeField[InstPatternMaker, _ <: Data] with HasMoreSignalInfo] = 
    genSeq(fieldMap)
}

