package sirius

import chisel3._
import chisel3.util.experimental.decode._
import scala.collection.immutable.ListMap
import cpuutil.CanAutoGenSig
import org.chipsalliance.rvdecoderdb

case class CoreConfig(
  // Debug
  val isDebug: Boolean = true,
  val perf: Boolean = true,
  val ysyxsoc: Boolean = true,

  // rvdecoderdb
  val rvOpCodesPath:     os.Path = os.pwd / "rvdecoderdb" / "riscv-opcodes",
  val curtomOpCodesPath: Iterable[os.Path] = None,
  val OpCodesFilter:     (Iterable[rvdecoderdb.Instruction]) => Iterable[
    rvdecoderdb.Instruction
  ] = _.filter(inst => inst.pseudoFrom.isEmpty && inst.ratified),

  // 基础配置
  val xlen:              Int = 32,
  val extensions:        () => Set[ExtTypeEnum.Type] = () => Set(
    ExtTypeEnum.I,
    ExtTypeEnum.Zicsr
  ),
  val registerAddrWidth: Int = 4,
  val registerReadPortNum: Int = 2,
  val memoryAddrWidth:     Int = 32,
  val pcInit: BigInt = 0x30000000,

  // CsrID
  val mvendorid: Int = 0x79737978,
  val marchid: Int = 26010008,
) {
  require(xlen == 32 || xlen == 64)
  val mxlen: Int = xlen
  val registerNum: Int = 1 << registerAddrWidth
  require(memoryAddrWidth <= 32)

  // 根据配置映射解码Pattern和Field
  def fieldMap: CfgMap[
    DecodeField[InstPattern, _ <: Data] with CanAutoGenSig,
    Seq[DecodeField[InstPattern, _ <: Data] with CanAutoGenSig]
  ] =
    CfgMap(
      ListMap(
        (Set(ExtTypeEnum.I), Set(32, 64)) -> InstFields.fieldRvI,
        (Set(ExtTypeEnum.Zicsr), Set(32)) -> InstFields.fieldRvZicsr,
      )
    )

  println(rvOpCodesPath)
  def patternMap(instPatterns: InstPatterns)
    : CfgMap[InstPattern, Seq[InstPattern]] =
    CfgMap(
      ListMap(
        (Set(ExtTypeEnum.I), Set(32, 64)) -> instPatterns.patternRvI,
        (Set(ExtTypeEnum.Zicsr), Set(32)) -> instPatterns.patternRvZicsr,
      )
    )
}
object CoreConfig {
  implicit val default: CoreConfig = CoreConfig()
}
