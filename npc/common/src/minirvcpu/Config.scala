package minirvcpu

case class CoreConfig(
  val rvOpcodesPath: os.Path = os.pwd / "rvdecoderdb" / "riscv-opcodes",
  val xlen: Int = 32,
  val extensions: Set[ExtTypeEnum.Type] = Set(ExtTypeEnum.I),
  val registerAddrWidth: Int = 4,
  val registerReadPortNum: Int = 2,
  val memoryAddrWidth: Int = 24,
  ) {
  require(xlen == 32 || xlen == 64 || xlen == 128)
  val registerNum: Int = 1 << registerAddrWidth
  require(memoryAddrWidth <= 32)
}
object CoreConfig {
  implicit val default: CoreConfig = CoreConfig()
}
