package playground.minirvcpu

object ExtType extends Enumeration {
  type ExtType = Value
  val I, M = Value
}

object InstType extends Enumeration {
  type InstType = Value
  val I = Value
}

case class CoreConfig(
  val xlen: Int = 32,
  val extensions: Seq[ExtType.ExtType] = Seq(ExtType.I),
  val registerAddrWidth: Int = 4,
  val registerReadPortNum: Int = 2,
  ) {
  require(Integer.bitCount(xlen) == 1)
  val registerNum: Int = 1 << registerAddrWidth
}
object CoreConfig {
  implicit val default: CoreConfig = CoreConfig()
}
