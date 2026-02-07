package sirius

import chisel3._
import chisel3.util.experimental.decode._
import scala.collection.immutable.ListMap
import cpuutil.CanAutoGenSig
import org.chipsalliance.rvdecoderdb

case class CoreConfig(
  // 基础配置
  val isDebug: Boolean = true,
  // val rvOpcodesPath: os.Path = os.pwd / "rvdecoderdb" / "riscv-opcodes",
  val rvInsts: Iterable[rvdecoderdb.Instruction] = rvdecoderdb
    .instructions(os.pwd / "rvdecoderdb" / "riscv-opcodes")
    .filter(inst => inst.pseudoFrom.isEmpty && inst.ratified),
  val xlen:                Int = 32,
  val extensions:          Set[ExtTypeEnum.Type] = Set(ExtTypeEnum.I),
  val registerAddrWidth:   Int = 4,
  val registerReadPortNum: Int = 2,
  val memoryAddrWidth:     Int = 32) {
  require(xlen == 32 || xlen == 64)
  val registerNum: Int = 1 << registerAddrWidth
  require(memoryAddrWidth <= 32)

  // 根据配置映射解码Pattern和Field
  val fieldMap: CfgMap[DecodeField[InstPattern, _ <: Data] with CanAutoGenSig] =
    CfgMap(
      this,
      ListMap(
        (Set(ExtTypeEnum.I), Set(32, 64)) -> InstFields.fieldsBase
      )
    )

  private val instPatterns = InstPatterns()(rvInsts, this)
  val patternMap: CfgMap[InstPattern] = CfgMap(
    this,
    ListMap(
      (Set(ExtTypeEnum.I), Set(32, 64)) -> instPatterns.patternBase
    )
  )
}
object CoreConfig {
  implicit val default: CoreConfig = CoreConfig()
}
