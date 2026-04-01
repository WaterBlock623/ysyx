package sirius

import chisel3._
import chisel3.util._

class Ifu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val exte = IO(new Bundle {
    val pcReg = new IfuToPcRegIO
    val mem = new Axi4IO
  })
  val out = IO(Decoupled(new IfuToIduIO))
  val debug = Option.when(cfg.isDebug)(IO(Output(UInt(cfg.xlen.W))))

  val pc = exte.pcReg.pc

  out.bits.ifuPayload.trap := 0.U.asTypeOf(
    chiselTypeOf(out.bits.ifuPayload.trap)
  )

  val outBits = out.bits

  val sIdle :: sWaitResp :: sKeepData :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val canValid = RegNext(RegNext(!reset.asBool))

  val nextState = MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(
        exte.mem.ar.fire,
        Mux(exte.mem.r.valid, Mux(out.fire, sIdle, sKeepData), sWaitResp),
        sIdle
      ),
      sWaitResp -> Mux(
        exte.mem.r.valid,
        Mux(out.fire, sIdle, sKeepData),
        sWaitResp
      ),
      sKeepData -> Mux(out.fire, sIdle, sKeepData)
    )
  )
  state := nextState

  exte.mem :<= 0.U.asTypeOf(new Axi4IO)
  exte.mem.ar.bits.size := "b010".U
  assert(!exte.mem.aw.valid && !exte.mem.w.valid && !exte.mem.b.valid)
  exte.mem.ar.valid := state === sIdle && canValid
  exte.mem.ar.bits.addr := pc
  exte.mem.r.ready := true.B

  val dataReg = RegEnable(
    exte.mem.r.bits.data,
    state =/= sKeepData && nextState === sKeepData
  )
  // out.valid := (state === sWaitResp && exte.mem.r.valid) || state === sKeepData
  // out.valid := state === sKeepData
  out.valid := exte.mem.r.valid || state === sKeepData
  // outBits.ifuPayload.ifu.inst := dataReg
  outBits.ifuPayload.ifu.inst := Mux(state === sKeepData, dataReg, exte.mem.r.bits.data)
  outBits.ifuPayload.ifu.pc := pc

  if (cfg.isDebug) {
    debug.get := outBits.ifuPayload.ifu.inst
  }
  PerfWhen(
    "instFetch",
    exte.mem.r.fire,
    out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
  )
  PerfWhen(
    "waitReadCyc",
    state === sIdle || state === sWaitResp,
    out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
  )
  PerfWhen(
    "keepDataCyc",
    state === sKeepData,
    out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
  )
}
