package sirius

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

class Lsu(
  implicit private val cfg: CoreConfig)
    extends Module {
  if (cfg.xlen != 32) {
    throw new IllegalArgumentException("Unsupported xlen")
  }

  val exte = IO(new Bundle {
    val mem = new LsuToMemIO
  })
  val in = IO(Flipped(Decoupled(new ExuToLsuIO)))
  val out = IO(Decoupled(new LsuToWbuIO))

  val inBits = in.bits
  val outBits = out.bits
  val ctrl = inBits.ctrl.lsuCtrl
  val addr = inBits.exuPayload.exu.aluOut

  // 数据透传
  outBits.lsuPayload.viewAsSupertype(new ExuPayload) := inBits.exuPayload
  outBits.ctrl := inBits.ctrl.viewAsSupertype(new WbuCtrl)

  // FSM
  val sIdle :: sWaitReadDone :: sWaitWriteDone :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val isMemAcc = ctrl.isLoad || ctrl.isStore

  state := MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(in.fire, 
        MuxCase(sIdle, Seq(
          ctrl.isLoad -> sWaitReadDone,
          ctrl.isStore -> sWaitWriteDone
          )), 
        sIdle),
      sWaitReadDone -> Mux(out.fire, sIdle, sWaitReadDone),
      sWaitWriteDone -> Mux(out.fire, sIdle, sWaitWriteDone),
    )
  )

  val queue = Module(new Queue(UInt(cfg.xlen.W), 1, flow = true))
  // enq
  queue.io.enq.valid := exte.mem.respValid && state === sWaitReadDone
  queue.io.enq.bits := exte.mem.rData
  // deq
  out.valid := queue.io.deq.valid
  val rData = queue.io.deq.bits
  queue.io.deq.ready := out.ready

  exte.mem.respReady := MuxLookup(state, false.B)(Seq(
    sWaitReadDone -> queue.io.enq.ready,
    sWaitWriteDone -> true.B
  ))

  val isBypass = state === sIdle && !isMemAcc
  val isCompleted = (state === sWaitReadDone || state === sWaitWriteDone) && exte.mem.respReady

  in.ready := isBypass || isCompleted
  out.valid := (in.fire && isBypass) || isCompleted

  val canSendReq = state === sIdle && in.valid && isMemAcc

  exte.mem.reqValid := canSendReq
  exte.mem.addr := addr
  exte.mem.wEn := ctrl.isStore

  val rem = addr(1, 0)
  // load
  val byteData = rData.asTypeOf(Vec(cfg.xlen >> 3, UInt(8.W)))
  val lbu = byteData(rem)
  val lbData = Mux(
    ctrl.isUnsignedLoad,
    0.U((cfg.xlen - 8).W),
    Fill(cfg.xlen - 8, lbu(7))
  ) ## lbu
  val lhu = Mux(rem(1), rData(31, 16), rData(15, 0))
  val lhData = Mux(
    ctrl.isUnsignedLoad,
    0.U((cfg.xlen - 16).W),
    Fill(cfg.xlen - 16, lhu(15))
  ) ## lhu
  val lwData = rData(31, 0)
  outBits.lsuPayload.lsu.loadData := MuxLookup(ctrl.loadStoreLength, lwData)(
    Seq(
      LoadStoreLengthEnum.w.asUInt -> lwData,
      LoadStoreLengthEnum.h.asUInt -> lhData,
      LoadStoreLengthEnum.b.asUInt -> lbData
    )
  )

  // store
  val regData = inBits.exuPayload.idu.rs2Data
  val sb = (regData(7, 0) << (rem * 8.U)).pad(cfg.xlen)
  val sh = (regData(15, 0) << (rem(1) * 16.U)).pad(cfg.xlen)
  val sw = regData(31, 0).pad(cfg.xlen)
  exte.mem.wData := MuxLookup(ctrl.loadStoreLength, sw)(
    Seq(
      LoadStoreLengthEnum.w.asUInt -> sw,
      LoadStoreLengthEnum.h.asUInt -> sh,
      LoadStoreLengthEnum.b.asUInt -> sb
    )
  )
  val sbMask = (1.U << rem).pad(cfg.xlen)
  val shMask = (3.U << (rem & 2.U)).pad(cfg.xlen)
  val swMask = 15.U(cfg.xlen.W)
  exte.mem.wMask := MuxLookup(ctrl.loadStoreLength, swMask)(
    Seq(
      LoadStoreLengthEnum.w.asUInt -> swMask,
      LoadStoreLengthEnum.h.asUInt -> shMask,
      LoadStoreLengthEnum.b.asUInt -> sbMask
    )
  )
}
