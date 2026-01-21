package minirvcpu

case class CoreConfig(
  val xlen: Int = 32,
  val extensions: List[ExtTypeEnum.Type] = List(ExtTypeEnum.I),
  val registerAddrWidth: Int = 4,
  val registerReadPortNum: Int = 2,
  val memoryAddrWidth: Int = 24,
  ) {
  require(Integer.bitCount(xlen) == 1)
  val registerNum: Int = 1 << registerAddrWidth
  require(memoryAddrWidth <= 32)
}
object CoreConfig {
  implicit val default: CoreConfig = CoreConfig()
}
