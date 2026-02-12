package sirius

import chisel3._
import scala.collection.immutable.ListMap

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
    (0xB80, 0xB00) -> (() => new CsrMcycle32)
  ),
  val csrMap: ListMap[Int, () => CsrParent] = ListMap(
    // "0xB00" -> (() => new CsrMcycle),
    // "0xB80" -> (() => new CsrMcycleh)
  ),
)
object UnitConfig {
  implicit val default: UnitConfig = UnitConfig()
}
