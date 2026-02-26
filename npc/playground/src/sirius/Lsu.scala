package sirius

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup
import chisel3.experimental.dataview._
import chisel3.util.Decoupled

class Lsu(implicit private val cfg: CoreConfig) extends Module {
  val exte = IO(new Bundle {
    val mem = new LsuToMemIO
  })
  val in = IO(Flipped(Decoupled(new ExuToLsuIO)))
  val out = IO(Decoupled(new LsuToWbuIO))


  // DecoupledIO
  // DecoupledMasterSlaveFsm(out, in)
  // in.ready := true.B
  // out.valid := in.valid
  val inBits = in.bits
  val outBits = out.bits

  val ctrl = inBits.ctrl.lsuCtrl

  import DecoupledState._
  val state = RegInit(sIdle)
  val isMemAcc = ctrl.isLoad || ctrl.isStore
  state := MuxLookup(state, sIdle)(Seq(
    sIdle -> Mux(in.valid && isMemAcc, sBusy, sIdle),
    sBusy -> Mux(exte.mem.respValid, sIdle, sBusy),
    // sWait -> sIdle
    ))
  val isRespValid = state === sBusy && exte.mem.respValid
  in.ready := isRespValid
  out.valid := Mux(state === sIdle && !isMemAcc, in.valid, isRespValid)

  outBits.lsuPayload.viewAsSupertype(new ExuPayload) := inBits.exuPayload
  outBits.ctrl := inBits.ctrl.viewAsSupertype(new WbuCtrl)

  if (cfg.xlen != 32) {
    throw new IllegalArgumentException("Unsupported xlen")
  }


  val addr = inBits.exuPayload.exu.aluOut
  exte.mem.reqValid := !reset.asBool && state === sIdle && isMemAcc && in.valid
  exte.mem.addr := addr
  exte.mem.wEn := ctrl.isStore

  val rem = addr(1, 0)
  // load
  val rData = exte.mem.rData
  val byteData = rData.asTypeOf(Vec(cfg.xlen >> 3, UInt(8.W)))
  val lbu = byteData(rem)
  val lbData = Mux(ctrl.isUnsignedLoad, 0.U((cfg.xlen - 8).W), 
    Fill(cfg.xlen - 8, lbu(7))) ## lbu
  val lhu = Mux(rem(1), rData(31, 16), rData(15, 0)) 
  val lhData = Mux(ctrl.isUnsignedLoad, 0.U((cfg.xlen - 16).W), 
    Fill(cfg.xlen - 16, lhu(15))) ## lhu
  val lwData = rData(31, 0)
  outBits.lsuPayload.lsu.loadData := MuxLookup(ctrl.loadStoreLength, lwData)(Seq(
    LoadStoreLengthEnum.w.asUInt -> lwData,
    LoadStoreLengthEnum.h.asUInt -> lhData,
    LoadStoreLengthEnum.b.asUInt -> lbData,
    ))

  // store
  val regData = inBits.exuPayload.idu.rs2Data
  val sb = (regData(7, 0) << (rem * 8.U)).pad(cfg.xlen)
  val sh = (regData(15, 0) << (rem(1) * 16.U)).pad(cfg.xlen)
  val sw = regData(31, 0).pad(cfg.xlen)
  exte.mem.wData := MuxLookup(ctrl.loadStoreLength, sw)(Seq(
    LoadStoreLengthEnum.w.asUInt -> sw,
    LoadStoreLengthEnum.h.asUInt -> sh,
    LoadStoreLengthEnum.b.asUInt -> sb,
    ))
  val sbMask = (1.U << rem).pad(cfg.xlen)
  val shMask = (3.U << (rem & 2.U)).pad(cfg.xlen)
  val swMask = 15.U(cfg.xlen.W)
  exte.mem.wMask := MuxLookup(ctrl.loadStoreLength, swMask)(Seq(
    LoadStoreLengthEnum.w.asUInt -> swMask,
    LoadStoreLengthEnum.h.asUInt -> shMask,
    LoadStoreLengthEnum.b.asUInt -> sbMask,
    ))
}





