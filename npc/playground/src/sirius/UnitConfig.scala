package sirius

import chisel3._
import scala.collection.immutable.ListMap

object CsrAddr {
  val mcycle = 0xB00
  val mcycleh = 0xB80
  val mvendorid = 0xF11
  val marchid = 0xF12
  val mtvec = 0x305
  val mepc = 0x341
  val mcause = 0x342
  val mstatus = 0x300
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
    CsrAddr.mtvec -> (() => new CsrMtvec),
    CsrAddr.mepc -> (() => new CsrMepc),
    CsrAddr.mcause -> (() => new CsrMcause),
    CsrAddr.mstatus -> (() => new CsrMstatus),
  ))
object UnitConfig {
  implicit val default: UnitConfig = UnitConfig()
}
