package minirvcpu

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import scala.collection.immutable.ListMap
import scala.language.dynamics

trait HasMoreSignalInfo {
  def extType: ExtType.Value
  def stage: String
  def chiselType: Data
}

case class InstPatternMaker(
  name: String,
  bp: String,
  instType: InstType.InstType,
  isWriteBackReg: Boolean = false,
  ) extends DecodePattern {
    def bitPat: BitPat = BitPat("b" + bp)
}

object InstPatterns {
  import InstType._
  val instsBase: Seq[InstPatternMaker] = Seq(
    InstPatternMaker("addi", "???????????? ????? 000 ????? 0010011", I, 
      isWriteBackReg = true),
    )
//  val instsExtM: Seq[InstPatternMaker] = Seq.empty
}

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

class CtrlSignals extends Bundle {
  val wb = new Bundle {
    val isWriteBackReg = Bool()
  }
}

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

abstract class DynamicRecord(elementsMap: ListMap[String, Data]) extends Record with Dynamic {
  val elements = elementsMap
  def selectDynamic(name: String): Data = {
    elements.getOrElse(name, throw new IllegalArgumentException(
      s"Field $name not found in Record. Available: ${elements.keys.mkString(", ")}"))
  }
}

class StageSignals(fields: Seq[DecodeField[InstPatternMaker, _] with HasMoreSignalInfo])
  extends DynamicRecord(ListMap(fields.map(f => f.name -> f.chiselType.cloneType): _*))

class AutoCtrlSignals(allFields: Seq[DecodeField[InstPatternMaker, _] with HasMoreSignalInfo])
  extends DynamicRecord({
    val grouped = allFields.groupBy(_.stage)
    ListMap(grouped.toSeq.sortBy(_._1).map { case (stageName, stageFields) =>
      stageName -> new StageSignals(stageFields)
    }: _*)
  })

class IDU(implicit val cfg: CoreConfig) extends Module {
  val inst = IO(Input(UInt(cfg.xlen.W)))

  val decodeCollector = InstDecodeCollector()
  val ctrlSignals = IO(new AutoCtrlSignals(decodeCollector.allFields))

  val decodeTable = new DecodeTable(decodeCollector.allPatterns, decodeCollector.allFields)
  val decodeResult = decodeTable.decode(inst)
  decodeCollector.allFields.foreach { f =>
    ctrlSignals.elements(f.stage).asInstanceOf[Bundle].elements(f.name) := 
      decodeResult(f.asInstanceOf[DecodeField[_, _ <: Data]])
  }
}









