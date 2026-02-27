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

  // FSM
  import DecoupledState._
  val state = RegInit(sBusy)
  state := MuxLookup(state, sBusy)(
    Seq(
      sBusy -> Mux(exte.mem.reqReady, sWait, sBusy),
      sWait -> Mux(out.fire, sBusy, sWait)
    )
  )

  exte.mem.reqValid := state === sBusy

  val queue = Module(new Queue(UInt(cfg.xlen.W), 1, flow = true))
  // enq
  queue.io.enq.valid := exte.mem.respValid
  queue.io.enq.bits := exte.mem.rData
  exte.mem.respReady := queue.io.enq.ready
  // deq
  out.valid := queue.io.deq.valid
  outBits.ifuPayload.ifu.inst := queue.io.deq.bits
  queue.io.deq.ready := out.ready
  
  // pc
  val pc = exte.pcReg.pc
  exte.mem.rAddr := pc
  outBits.ifuPayload.ifu.pc := exte.pcReg.pc

  if (cfg.isDebug) {
    debug.get := out.bits.ifuPayload.ifu.inst
  }
}
