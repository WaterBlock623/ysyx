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
    val mem = new Axi4IO
  })
  val in = IO(Flipped(Decoupled(new ExuToLsuIO)))
  val out = IO(Decoupled(new LsuToWbuIO))

  val inBits = in.bits
  val outBits = out.bits
  val ctrl = inBits.ctrl.lsuCtrl
  val addr = inBits.exuPayload.exu.aluOut
  val isMemAcc = ctrl.isLoad || ctrl.isStore
  val canValid = RegNext(RegNext(!reset.asBool))
  val rData = exte.mem.r.bits.data
  val rem = addr(1, 0)

  // 数据透传
  outBits.lsuPayload.viewAsSupertype(new ExuPayload) := inBits.exuPayload
  outBits.ctrl := inBits.ctrl.viewAsSupertype(new WbuCtrl)

  exte.mem :<= 0.U.asTypeOf(chiselTypeOf(exte.mem))

  val eLoadStoreAddressMisaligned = isMemAcc &&
      ((ctrl.loadStoreLength === LoadStoreLengthEnum.h.asUInt && addr(0) =/= 0.U) ||
      (ctrl.loadStoreLength === LoadStoreLengthEnum.w.asUInt && rem =/= 0.U))
  val eLoadAddressMisaligned = ctrl.isLoad && eLoadStoreAddressMisaligned
  val eStoreAddressMisaligned = ctrl.isStore && eLoadStoreAddressMisaligned
  val eLoadAccessFault = ctrl.isLoad && 
    !(exte.mem.r.bits.resp === Axi4Resp.okay.U || exte.mem.r.bits.resp === Axi4Resp.exokay.U)
  val eStoreAccessFault = ctrl.isStore && 
    !(exte.mem.b.bits.resp === Axi4Resp.okay.U || exte.mem.b.bits.resp === Axi4Resp.exokay.U)

  val eCause = MuxCase(0.U, Seq(
    eStoreAccessFault -> McauseEnum.StoreOrAmoAccessFault.U,
    eLoadAccessFault -> McauseEnum.LoadAccessFault.U,
    eStoreAddressMisaligned -> McauseEnum.StoreOrAmoAddressMisaligned.U,
    eLoadAddressMisaligned -> McauseEnum.LoadAddressMisaligned.U
    ))

  when (!inBits.exuPayload.trap.isTrap) {
    outBits.lsuPayload.trap.isTrap := eLoadStoreAddressMisaligned || eLoadAccessFault || eStoreAccessFault
    outBits.lsuPayload.trap.cause := eCause
  }

  val sIdle :: sWaitAddrReady :: sWaitDataReady :: sWaitResp :: Nil = Enum(4)
  val state = RegInit(sIdle)

  // val canSendReq = state === sIdle && in.valid && isMemAcc

  state := MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(
        in.valid && isMemAcc && canValid && !outBits.lsuPayload.trap.isTrap,
        MuxCase(
          sIdle,
          Seq(
            (exte.mem.ar.ready && ctrl.isLoad) -> sWaitResp,
            (exte.mem.aw.ready && exte.mem.w.ready && ctrl.isStore) -> sWaitResp,
            (exte.mem.aw.ready && ctrl.isStore) -> sWaitDataReady,
            (exte.mem.w.ready && ctrl.isStore) -> sWaitAddrReady
          )
        ),
        sIdle
      ),
      sWaitAddrReady -> Mux(exte.mem.aw.ready, sWaitResp, sWaitAddrReady),
      sWaitDataReady -> Mux(exte.mem.w.ready, sWaitResp, sWaitDataReady),
      sWaitResp -> Mux(out.fire, sIdle, sWaitResp)
    )
  )

  val isTrap = in.valid && outBits.lsuPayload.trap.isTrap
  val isBypass = state === sIdle && in.valid && !isMemAcc
  val isMemDone =
    state === sWaitResp && ((ctrl.isLoad && exte.mem.r.valid) || (ctrl.isStore && exte.mem.b.valid))

  out.valid := isBypass || isMemDone || isTrap
  in.ready := out.fire

  val isRespReady = state === sWaitResp && out.ready
  exte.mem.r.ready := isRespReady && ctrl.isLoad
  exte.mem.b.ready := isRespReady && ctrl.isStore


  exte.mem.ar.bits.addr := addr
  exte.mem.aw.bits.addr := addr

  exte.mem.ar.valid := (state === sIdle) && in.valid && ctrl.isLoad && canValid && !eLoadStoreAddressMisaligned
  exte.mem.aw.valid := (state === sIdle || state === sWaitAddrReady) && in.valid && ctrl.isStore && canValid && !eLoadStoreAddressMisaligned
  exte.mem.w.valid := (state === sIdle || state === sWaitDataReady) && in.valid && ctrl.isStore && canValid && !eLoadStoreAddressMisaligned

  val axSize = MuxLookup(ctrl.loadStoreLength, "b010".U)(
    Seq(
      LoadStoreLengthEnum.w.asUInt -> "b010".U,
      LoadStoreLengthEnum.h.asUInt -> "b001".U,
      LoadStoreLengthEnum.b.asUInt -> "b000".U
    )
  )
  exte.mem.ar.bits.size := axSize
  exte.mem.aw.bits.size := axSize


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

  exte.mem.w.bits.data := MuxLookup(ctrl.loadStoreLength, sw)(
    Seq(
      LoadStoreLengthEnum.w.asUInt -> sw,
      LoadStoreLengthEnum.h.asUInt -> sh,
      LoadStoreLengthEnum.b.asUInt -> sb
    )
  )

  val sbMask = (1.U << rem).pad(cfg.xlen)
  val shMask = (3.U << (rem & 2.U)).pad(cfg.xlen)
  val swMask = 15.U(cfg.xlen.W)

  exte.mem.w.bits.strb := MuxLookup(ctrl.loadStoreLength, swMask)(
    Seq(
      LoadStoreLengthEnum.w.asUInt -> swMask,
      LoadStoreLengthEnum.h.asUInt -> shMask,
      LoadStoreLengthEnum.b.asUInt -> sbMask
    )
  )

  PerfWhen("memoryRead", exte.mem.r.fire, in.bits.ctrl.debugCtrl.get.isEbreak)
  PerfWhen("waitRead", in.valid && ctrl.isLoad && !outBits.lsuPayload.trap.isTrap, in.bits.ctrl.debugCtrl.get.isEbreak)
  PerfWhen("memoryWrite", exte.mem.b.fire, in.bits.ctrl.debugCtrl.get.isEbreak)
  PerfWhen("waitWrite", in.valid && ctrl.isStore && !outBits.lsuPayload.trap.isTrap, in.bits.ctrl.debugCtrl.get.isEbreak)
}
