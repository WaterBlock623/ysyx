package sirius

import chisel3._
import scala.collection.immutable.ListMap

object CsrAddr {
  val mcycle = 0xB00
  val mcycleh = 0xB80
  val mvendorid = 0xF11
  val marchid = 0xF12
}

case class UnitConfig(
  val aluMap: () => CfgMap[
    (ExuOutSelEnum.Type, () => AluParent),
    ListMap[ExuOutSelEnum.Type, () => AluParent]
  ] = () =>
    CfgMap(
      ListMap(
        (Set(ExtTypeEnum.I), Set(32, 64)) -> ListMap(
          ExuOutSelEnum.aluBase -> (() => new AluBase)
        )
      )
    ),
  val csr32Map: ListMap[(Int, Int), () => CsrParent32] = ListMap(
    (CsrAddr.mcycleh, CsrAddr.mcycle) -> (() => new CsrMcycle32)
  ),
  val csrMap: ListMap[Int, () => CsrParent] = ListMap(
    CsrAddr.mvendorid -> (() => new CsrMvendorid),
    CsrAddr.marchid -> (() => new CsrMarchid),
  ))
object UnitConfig {
  implicit val default: UnitConfig = UnitConfig()
}
