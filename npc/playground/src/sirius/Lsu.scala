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

  val sIdle :: sWaitResp :: Nil = Enum(2)
  val state = RegInit(sIdle)
  val isMemAcc = ctrl.isLoad || ctrl.isStore

  val canSendReq = state === sIdle && in.valid && isMemAcc
  exte.mem.reqValid := canSendReq

  state := MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(canSendReq && exte.mem.reqReady, sWaitResp, sIdle),
      sWaitResp -> Mux(out.fire, sIdle, sWaitResp)
    )
  )

  val isBypass = state === sIdle && in.valid && !isMemAcc
  val isMemDone = state === sWaitResp && exte.mem.respValid

  out.valid := isBypass || isMemDone
  
  in.ready := out.fire

  exte.mem.respReady := state === sWaitResp && out.ready

  exte.mem.addr := addr
  exte.mem.wEn := ctrl.isStore

  val rData = exte.mem.rData
  val rem = addr(1, 0)

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
  
  val lwData = rData

  outBits.lsuPayload.lsu.loadData := MuxLookup(ctrl.loadStoreLength, lwData)(
    Seq(
      LoadStoreLengthEnum.w.asUInt -> lwData,
      LoadStoreLengthEnum.h.asUInt -> lhData,
      LoadStoreLengthEnum.b.asUInt -> lbData
    )
  )

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
