package sirius

import chisel3._

class Ifu(implicit private val cfg: CoreConfig) extends Module {
  val exte = IO(new Bundle {
    val pcReg = new IfuToPcRegIO
    val mem = new IfuToMemIO
  })
  val out = IO(new IfuToIduIO)

  val pc = exte.pcReg.pc
  exte.mem.rAddr := pc 
  val inst = exte.mem.rData
  out.ifuPayload.ifu.pc := exte.pcReg.pc
  out.ifuPayload.ifu.inst := inst
}
