package minirvcpu

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import scala.collection.immutable.ListMap
import cpuutil.CanAutoGenSig
import org.chipsalliance.rvdecoderdb

trait HasMoreSignalInfo extends CanAutoGenSig {
  def extType: ExtTypeEnum.Type
}

object ExtTypeEnum extends ChiselEnum {
  val I, M = Value
}

object InstTypeEnum extends ChiselEnum {
  val R, I, S, B, U, J = Value
}

object AluInSelEnum extends ChiselEnum {
  val imm, rs2 = Value
}

object AluOpEnum extends ChiselEnum {
  val add, sub = Value
}

object ExuOutSelEnum extends ChiselEnum {
  val aluBase = Value
}

object JumpAddrSelEnum extends ChiselEnum {
  val imm, alu = Value
}


/*
// 指令属性
case class InstPatternMaker(
  name: String,
  bp: String,
  extType: ExtTypeEnum.Type,
  instType: InstTypeEnum.Type,
  isWriteBackReg: Boolean = false,
  aluSrc2: Data = DontCare,
  aluOp: Data = DontCare,
  isBranch: Boolean = false,
  branchValSrc: Data = DontCare,
  ) extends DecodePattern {
    def bitPat: BitPat = BitPat("b" + bp)
}

// 指令定义
object InstPatterns {
  import InstTypeEnum._
  import AluSourceEnum._
  import AluOpEnum._

  val instsBase: Seq[InstPatternMaker] = Seq(
    InstPatternMaker("addi", "???????????? ????? 000 ????? 0010011", ExtTypeEnum.I, I, 
      isWriteBackReg = true, aluSrc2 = imm, aluOp = add),
    )
//  val instsExtM: Seq[InstPatternMaker] = Seq.empty
}
*/

// 指令模板
case class InstPattern(inst: rvdecoderdb.Instruction) extends DecodePattern {
  def bitPat: BitPat = BitPat("b" + inst.encoding.toString)

  def inArgs(field: String): Boolean = {
    this.inst.args.map(_.toString()).contains(field)
  }
}

// 控制信号定义
object InstFields {
  private def bitPatEnum(e: EnumType): BitPat = BitPat(e.litValue.U((e.getWidth).W))

  val fieldsBase = Seq(
    new BoolDecodeField[InstPattern] with HasMoreSignalInfo {
      def name = "isWriteBackReg"
      def extType = ExtTypeEnum.I
      def stage = "wb"
      def genTable(i: InstPattern) = if (rvdecoderdb.Utils.writeRd(i.inst)) y else n
    },
    
    new BoolDecodeField[InstPattern] with HasMoreSignalInfo {
      def name = "isBranch"
      def extType = ExtTypeEnum.I
      def stage = "wb"
      def genTable(i: InstPattern) = if (rvdecoderdb.Utils.isB(i.inst)) y else n
    },

    new BoolDecodeField[InstPattern] with HasMoreSignalInfo {
      def name = "isJump"
      def extType = ExtTypeEnum.I
      def stage = "wb"
      def genTable(i: InstPattern) = i.inst.name match {
        case "jal" | "jalr" => y
        case _ => n
      }
    },

    new DecodeField[InstPattern, UInt] with HasMoreSignalInfo {
      def name = "aluIn2Sel"
      def extType = ExtTypeEnum.I
      def stage = "ex"
      def chiselType = UInt(AluInSelEnum.getWidth.W)
      def genTable(i: InstPattern) = {
        if (i.inArgs("rs2")) {
          bitPatEnum(AluInSelEnum.rs2)
        } else if (i.inArgs("imm12") ||
                   i.inArgs("imm20") ||
                   i.inArgs("imm12lo") ||
                   i.inArgs("bimm12lo")) {
          bitPatEnum(AluInSelEnum.imm)
        } else {
          dc
        }
      }
    },

    new DecodeField[InstPattern, UInt] with HasMoreSignalInfo {
      def name = "aluOp"
      def extType = ExtTypeEnum.I
      def stage = "ex"
      def chiselType = UInt(AluOpEnum.getWidth.W)
      def genTable(i: InstPattern) = i.inst.name match {
        case "addi" => bitPatEnum(AluOpEnum.add)
        case _ => dc
      }
    },

    new DecodeField[InstPattern, UInt] with HasMoreSignalInfo {
      def name = "exuOutSel"
      def extType = ExtTypeEnum.I
      def stage = "ex"
      def chiselType = UInt(ExtTypeEnum.getWidth.W)
      def genTable(i: InstPattern) = {
        InstructionSetParser.parse(i.inst.instructionSet.name) match {
          case (format, n, x, y) => 
            if (x == "i" || y == "i") {
              bitPatEnum(ExuOutSelEnum.aluBase)
            } else {
              dc
            }
        }
      }    
    },

    new DecodeField[InstPattern, UInt] with HasMoreSignalInfo {
      def name = "instType"
      def extType = ExtTypeEnum.I
      def stage = "id"
      def chiselType = UInt(InstTypeEnum.getWidth.W)
      def genTable(i: InstPattern) = {
        if (rvdecoderdb.Utils.isR(i.inst)) {
          bitPatEnum(InstTypeEnum.R)
        } else if (rvdecoderdb.Utils.isI(i.inst)) {
          bitPatEnum(InstTypeEnum.I)
        } else if (rvdecoderdb.Utils.isS(i.inst)) {
          bitPatEnum(InstTypeEnum.S)
        } else if (rvdecoderdb.Utils.isB(i.inst)) {
          bitPatEnum(InstTypeEnum.B)
        } else if (rvdecoderdb.Utils.isU(i.inst)) {
          bitPatEnum(InstTypeEnum.U)
        } else if (rvdecoderdb.Utils.isJ(i.inst)) {
          bitPatEnum(InstTypeEnum.J)
        } else {
          dc
        }
      }
    },

    new DecodeField[InstPattern, UInt] with HasMoreSignalInfo {
      def name = "jumpAddrSel"
      def extType = ExtTypeEnum.I
      def stage = "wb"
      def chiselType = UInt(JumpAddrSelEnum.getWidth.W)
      def genTable(i: InstPattern) = {
        if (i.inst.name == "jal") {
          bitPatEnum(JumpAddrSelEnum.imm)          
        } else if (i.inst.name == "jalr" || rvdecoderdb.Utils.isB(i.inst)) {
          bitPatEnum(JumpAddrSelEnum.alu)
        } else {
          dc
        }
      }
    },
  )
}

object InstructionSetParser {
  def parse(name: String): (String, Int, String, String) = {
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

// 根据启用的扩展生成Seq[DecodePattern]和Seq[DecodeField]
case class InstDecodeCollector()(implicit private val cfg: CoreConfig) {
  // 生成Fields
  private val fieldMap = Map(
    ExtTypeEnum.I -> InstFields.fieldsBase,
//    ExtTypeEnum.M -> InstFields.fieldsExtM,
    )
  private def genSeq[T](m: Map[ExtTypeEnum.Type, Seq[T]]): Seq[T] = {
    cfg.extensions.flatMap { key =>
      m.getOrElse(key, 
        throw new IllegalArgumentException(s"Unsupported extension: $key is not in $m"))
    }.toSeq
  }
  val allFields: Seq[DecodeField[InstPattern, _ <: Data] with HasMoreSignalInfo] = 
    genSeq(fieldMap)

  // 生成Patterns
  private val patternMap = Map(
    ExtTypeEnum.I -> Seq("i"),
//    ExtTypeEnum.M -> "m",
    )
  val extSet = genSeq(patternMap).toSet
  val allInsts = rvdecoderdb.instructions(cfg.rvOpcodesPath)
  val insts = allInsts.filter(inst => inst.pseudoFrom.isEmpty && inst.ratified)
    .filter { inst =>
      InstructionSetParser.parse(inst.instructionSet.name) match {
      case (format, n, x, y) =>
          if (format == "nx") {
            n == cfg.xlen && extSet.contains(x)
          } else if (format == "x") {
            extSet.contains(x)
          } else if (format == "xy") {
            extSet.contains(x) && extSet.contains(y)
          } else {
            false
          }
        case _ => false
      }
    }
  val allPatterns: Seq[InstPattern] = insts.map(InstPattern(_)).toSeq
}

