package sirius

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup
import chisel3.experimental.dataview._

class Lsu(implicit private val cfg: CoreConfig) extends Module {
  val exte = IO(new Bundle {
    val mem = new LsuToMemIO
  })
  val in = IO(Flipped(new ExuToLsuIO))
  val out = IO(new LsuToWbuIO)

  out.lsuPayload.viewAsSupertype(new ExuPayload) := in.exuPayload
  out.ctrl := in.ctrl.viewAsSupertype(new WbuCtrl)

  if (cfg.xlen != 32) {
    throw new IllegalArgumentException("Unsupported xlen")
  }

  val ctrl = in.ctrl.lsuCtrl
  exte.mem.valid := ctrl.isLoad || ctrl.isStore
  exte.mem.wEn := ctrl.isStore
  val addr = in.exuPayload.exu.aluOut
  exte.mem.rAddr := addr
  exte.mem.wAddr := addr

  val rem = addr(1, 0)

  // load
  val rData = exte.mem.rData
  val lbu = 0.U(1.W) ## (rData >> rem * 8.U)(6, 0)
  val lbData = Mux(ctrl.isUnsignedLoad, 0.U((cfg.xlen - 8).W), 
    Fill(cfg.xlen - 8, lbu(7))) ## lbu
  val lhu = (rData >> (rem(1) * 16.U))(15, 0)
  val lhData = Mux(ctrl.isUnsignedLoad, 0.U((cfg.xlen - 16).W), 
    Fill(cfg.xlen - 16, lhu(15))) ## lhu
  val lwData = rData(31, 0).pad(cfg.xlen)
  out.lsuPayload.lsu.loadData := MuxLookup(ctrl.loadStoreLength, lwData)(Seq(
    LoadStoreLengthEnum.w.asUInt -> lwData,
    LoadStoreLengthEnum.h.asUInt -> lhData,
    LoadStoreLengthEnum.b.asUInt -> lbData,
    ))

  // store
  val regData = in.exuPayload.idu.rs2Data
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





