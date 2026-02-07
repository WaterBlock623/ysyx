package sirius

import chisel3._
import scala.collection.immutable.ListMap

case class UnitConfig(
  val aluMap: CfgMap[
    (ExuOutSelEnum.Type, AluParent),
    ListMap[ExuOutSelEnum.Type, AluParent]
  ] = CfgMap(
    ListMap(
      (Set(ExtTypeEnum.I), Set(32, 64)) -> ListMap(
        ExuOutSelEnum.aluBase -> new AluBase
      )
    )
  ))
object UnitConfig {
  implicit val default: UnitConfig = UnitConfig()
}
