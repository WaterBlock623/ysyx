package sirius

import chisel3._
import chisel3.util.experimental.decode._
import scala.collection.immutable.ListMap
import cpuutil.CanAutoGenSig
import org.chipsalliance.rvdecoderdb

class CoreConfig(
  // Debug
  val isDebug: Boolean = true,

  // rvdecoderdb
  val rvOpCodesPath:     os.Path = os.pwd / "rvdecoderdb" / "riscv-opcodes",
  val curtomOpCodesPath: Iterable[os.Path] = None,
  val OpCodesFilter:     (Iterable[rvdecoderdb.Instruction]) => Iterable[
    rvdecoderdb.Instruction
  ] = _.filter(inst => inst.pseudoFrom.isEmpty && inst.ratified),

  // 基础配置
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

  println(rvOpCodesPath)
  def patternMap(instPatterns: InstPatterns): CfgMap[InstPattern] = CfgMap(
    this,
    ListMap(
      (Set(ExtTypeEnum.I), Set(32, 64)) -> instPatterns.patternBase
    )
  )
}
object CoreConfig {
  implicit val default: CoreConfig = new CoreConfig()
}
