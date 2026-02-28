package sirius

import chisel3._
import chisel3.util._

class Ifu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val exte = IO(new Bundle {
    val pcReg = new IfuToPcRegIO
    val mem = new Axi4LiteIO
  })
  val out = IO(Decoupled(new IfuToIduIO))
  val debug = Option.when(cfg.isDebug)(IO(Output(UInt(cfg.xlen.W))))

  val outBits = out.bits

  val sIdle :: sWaitResp :: Nil = Enum(2)
  val state = RegInit(sIdle)
  val canValid = RegNext(RegNext(!reset.asBool))

  exte.mem :<= 0.U.asTypeOf(new Axi4LiteIO)

  exte.mem.ar.valid := state === sIdle && canValid

  state := MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(exte.mem.ar.ready && canValid, sWaitResp, sIdle),
      sWaitResp -> Mux(out.fire, sIdle, sWaitResp)
    )
  )

  out.valid := state === sWaitResp && exte.mem.r.valid
  outBits.ifuPayload.ifu.inst := exte.mem.r.bits.data
  
  val pc = exte.pcReg.pc
  exte.mem.ar.bits.addr := pc
  outBits.ifuPayload.ifu.pc := pc

  exte.mem.r.ready := state === sWaitResp && out.ready

  if (cfg.isDebug) {
    debug.get := outBits.ifuPayload.ifu.inst
  }
}
