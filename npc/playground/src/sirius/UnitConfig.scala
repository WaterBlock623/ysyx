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
  val csr32Map: ListMap[(Int, Int), CoreConfig => CsrParent32] = ListMap(
    (CsrAddr.mcycleh, CsrAddr.mcycle) -> (cfg => new CsrMcycle32()(cfg))
  ),
  val csrMap: ListMap[Int, CoreConfig => CsrParent] = ListMap(
    CsrAddr.mvendorid -> (cfg => new CsrMvendorid()(cfg)),
    CsrAddr.marchid -> (cfg => new CsrMarchid()(cfg)),
    CsrAddr.mtvec -> (cfg => new CsrMtvec()(cfg)),
    CsrAddr.mepc -> (cfg => new CsrMepc()(cfg)),
    CsrAddr.mcause -> (cfg => new CsrMcause()(cfg)),
    CsrAddr.mstatus -> (cfg => new CsrMstatus()(cfg)),
  )) {
}
object UnitConfig {
  implicit val default: UnitConfig = UnitConfig()
}
