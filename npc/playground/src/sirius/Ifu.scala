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

  val outBits = dontTouch(Wire(chiselTypeOf(out.bits)))

  // FSM
  import DecoupledState._
  val state = RegInit(sBusy)
  state := MuxLookup(state, sBusy)(
    Seq(
      sBusy -> Mux(exte.mem.respValid, sWait, sBusy),
      sWait -> Mux(out.ready, sBusy, sWait)
    )
  )
  out.bits := Mux(
    exte.mem.reqValid,
    outBits,
    RegEnable(outBits, state === sBusy && exte.mem.respValid)
  )
  out.valid := (state === sBusy && exte.mem.respValid) || state === sWait
  // exte.mem.reqValid := !reset.asBool && (state === sIdle || (state === sWait && out.ready))
  // val isSBusy = state === sBusy
  // exte.mem.reqValid := isSBusy && !RegNext(isSBusy)
  exte.mem.reqValid := (state === sBusy && !exte.mem.respValid) || (state === sWait && out.ready)

  //
  // import chisel3.util.random.LFSR
  //
  // val lfsr = LFSR(4)
  // val delayReg = RegInit(0.U(4.W))
  // val isNewReq = state === sWait && !RegNext(state === sWait)
  //
  // when(isNewReq) {
  //   delayReg := lfsr
  // }.elsewhen(delayReg > 0.U) {
  //   delayReg := delayReg - 1.U
  // }
  //
  // state := MuxLookup(state, sBusy)(
  //   Seq(
  //     // sIdle -> Mux(out.ready, sBusy, sIdle),
  //     sBusy -> sWait,
  //     sWait -> Mux(out.fire, sBusy, sWait)
  //   )
  // )
  //
  // out.valid := state === sWait && delayReg === 0.U && !isNewReq
  //

  val pc = exte.pcReg.pc
  exte.mem.rAddr := pc
  val inst = exte.mem.rData
  outBits.ifuPayload.ifu.pc := exte.pcReg.pc
  outBits.ifuPayload.ifu.inst := inst

  if (cfg.isDebug) {
    debug.get := out.bits.ifuPayload.ifu.inst
  }
}
