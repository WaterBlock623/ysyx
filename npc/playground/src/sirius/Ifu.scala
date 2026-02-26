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

  // DecoupledIO
  DecoupledFsm(true, out)
  out.valid := true.B
  val outBits = out.bits

  // FSM
  import DecoupledState._
  val state = RegInit(sBusy)
  state := MuxLookup(state, sBusy)(
    Seq(
      // sIdle -> Mux(out.ready, sBusy, sIdle),
      sBusy -> sWait,
      sWait -> Mux(out.ready, sBusy, sWait)
    )
  )
  // out.valid := state === sWait

  //
  import chisel3.util.random.LFSR

  val lfsr = LFSR(8)
  val delayReg = RegInit(0.U(8.W))
  val isDelaying = delayReg > 0.U

  when(state === sWait && !isDelaying && out.ready) {
    delayReg := lfsr
  }.elsewhen(isDelaying) {
    delayReg := delayReg - 1.U
  }
  out.valid := state === sWait && !isDelaying
  //

  val pc = exte.pcReg.pc
  exte.mem.rAddr := pc
  val inst = exte.mem.rData
  outBits.ifuPayload.ifu.pc := exte.pcReg.pc
  outBits.ifuPayload.ifu.inst := inst

  if (cfg.isDebug) {
    debug.get := inst
  }
}
