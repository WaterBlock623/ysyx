package sirius

import chisel3._
import chisel3.util._

class Ifu(implicit private val cfg: CoreConfig) extends Module {
  val exte = IO(new Bundle {
    val pcReg = new IfuToPcRegIO
    val mem = new IfuToMemIO
  })
  val out = IO(Decoupled(new IfuToIduIO))
  val debug = Option.when(cfg.isDebug)(IO(Output(UInt(cfg.xlen.W))))

  // DecoupledIO
  DecoupledFsm(true, out)
  out.valid := true.B
  val outBits = out.bits

  val pc = exte.pcReg.pc
  exte.mem.rAddr := pc 
  val inst = exte.mem.rData
  outBits.ifuPayload.ifu.pc := exte.pcReg.pc
  outBits.ifuPayload.ifu.inst := inst

  if (cfg.isDebug) {
    debug.get := inst
  }
}
