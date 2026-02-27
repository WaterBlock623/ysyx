package sirius

import chisel3._
import chisel3.util._

class Ifu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val exte = IO(new Bundle {
    val pcReg = new IfuToPcRegIO
    val mem = new IfuToMemIO
  })
  val out = IO(Decoupled(new IfuToIduIO))
  val debug = Option.when(cfg.isDebug)(IO(Output(UInt(cfg.xlen.W))))

  val outBits = out.bits

  val sIdle :: sWaitResp :: Nil = Enum(2)
  val state = RegInit(sIdle)

  exte.mem.reqValid := state === sIdle

  state := MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(exte.mem.reqReady, sWaitResp, sIdle),
      sWaitResp -> Mux(out.fire, sIdle, sWaitResp)
    )
  )

  out.valid := state === sWaitResp && exte.mem.respValid
  outBits.ifuPayload.ifu.inst := exte.mem.rData
  
  val pc = exte.pcReg.pc
  exte.mem.rAddr := pc
  outBits.ifuPayload.ifu.pc := pc

  exte.mem.respReady := state === sWaitResp && out.ready

  if (cfg.isDebug) {
    debug.get := outBits.ifuPayload.ifu.inst
  }
}
