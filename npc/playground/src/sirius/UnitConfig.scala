package sirius

import chisel3._

case class UnitConfig(
  // val aluMap: CfgMap[Module] = 
  )
object UnitConfig {
  implicit val default: UnitConfig = UnitConfig()
}
