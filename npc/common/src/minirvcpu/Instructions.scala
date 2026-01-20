package minirvcpu

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import scala.collection.immutable.ListMap
import cpuutil.CanAutoGenSig

trait HasMoreSignalInfo extends CanAutoGenSig {
  def extType: ExtType.Value
}

object AluSourceEnum extends ChiselEnum {
  val imm, rd2 = Value
}

object AluOpEnum extends ChiselEnum {
  val add = Value
}

case class InstPatternMaker(
  name: String,
  bp: String,
  instType: InstType.InstType,
  isWriteBackReg: Boolean = false,
  aluSrc2: Data = DontCare,
  aluOp: Data = DontCare,
  ) extends DecodePattern {
    def bitPat: BitPat = BitPat("b" + bp)
}

// 指令定义
object InstPatterns {
  import InstType._
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
      def extType = ExtType.I
      def stage = "wb"
      def genTable(i: InstPatternMaker) = if (i.isWriteBackReg) y else n
    },
    
    new DecodeField[InstPatternMaker, UInt] with HasMoreSignalInfo {
      def name = "aluSrc2"
      def extType = ExtType.I
      def stage = "ex"
      def chiselType = UInt(AluSourceEnum.getWidth.W)
      def genTable(i: InstPatternMaker) = {
        i.aluSrc2 match {
          case e: AluSourceEnum.Type => BitPat(e.litValue.U(AluSourceEnum.getWidth.W))
          case DontCare => dc
          case v => throw new IllegalArgumentException(s"Invalid aluSrc2 value: $v")
        } 
      }
    },

    new DecodeField[InstPatternMaker, UInt] with HasMoreSignalInfo {
      def name = "aluOp"
      def extType = ExtType.I
      def stage = "ex"
      def chiselType = UInt(AluOpEnum.getWidth.W)
      def genTable(i: InstPatternMaker) = {
        i.aluOp match {
          case e: AluOpEnum.Type => BitPat(e.litValue.U(AluOpEnum.getWidth.W))
          case DontCare => dc
          case v => throw new IllegalArgumentException(s"Invalid aluOp value: $v")
        } 
      }
    },
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

