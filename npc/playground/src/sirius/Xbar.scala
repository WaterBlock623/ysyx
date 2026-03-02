package sirius

import chisel3._
import chisel3.util._

class MemBusArbiter(
  implicit private val cfg: CoreConfig)
    extends Module {
  val in = IO(Vec(2, Flipped(new Axi4LiteIO)))
  val out = IO(new Axi4LiteIO)

  val ifu = in(0)
  val lsu = in(1)

  val sIdle :: sIfu :: sLsu :: Nil = Enum(3)
  val state = RegInit(sIdle)

  val ifuValid = ifu.ar.valid
  val ifuReady = ifu.r.fire
  val lsuValid = lsu.ar.valid || lsu.aw.valid || lsu.w.valid
  val lsuReady = lsu.r.fire || lsu.b.fire

  state := MuxLookup(state, sIdle)(
    Seq(
      sIdle -> MuxCase(
        sIdle,
        Seq(
          ifuValid -> sIfu,
          lsuValid -> sLsu
        )
      ),
      sIfu -> Mux(ifuReady, sIdle, sIfu),
      sLsu -> Mux(lsuReady, sIdle, sLsu)
    )
  )

  0.U.asTypeOf(chiselTypeOf(ifu)) :>= ifu
  0.U.asTypeOf(chiselTypeOf(lsu)) :>= lsu
  out :<= 0.U.asTypeOf(chiselTypeOf(out))

  switch(state) {
    is(sIdle) {
      when(ifuValid) {
        out :<>= ifu
      }.elsewhen(lsuValid) {
        out :<>= lsu
      }
    }
    is(sIfu) {
      out :<>= ifu
    }
    is(sLsu) {
      out :<>= lsu
    }
  }
}

class Xbar(
  implicit private val cfg: CoreConfig)
    extends Module {
  val in = IO(Flipped(new Axi4LiteIO))
  val out = IO(Vec(2, new Axi4LiteIO))

  val mem = out(0)
  def isMemAddr(addr: UInt): Bool = addr >= "h80000000".U
  val uart = out(1)
  def isUartAddr(addr: UInt): Bool = addr === "h10000000".U

  out :<= 0.U.asTypeOf(chiselTypeOf(out))
  0.U.asTypeOf(chiselTypeOf(in)) :>= in

  val rAddrComb = in.ar.bits.addr
  val canUpdateRAddr = in.ar.valid
  val rAddrReg = RegEnable(
    rAddrComb,
    canUpdateRAddr
  )

  when (isMemAddr(rAddrComb)) {
    mem.ar :<>= in.ar
  } .elsewhen (isUartAddr(rAddrComb)) {
    uart.ar :<>= in.ar
  }

  when (isMemAddr(rAddrReg)) {
    in.r :<>= mem.r
  } .elsewhen (isUartAddr(rAddrReg)) {
    in.r :<>= uart.r
  } .otherwise {
    in.r.bits.resp := "b11".U
  }

  val wAddrComb = in.aw.bits.addr
  val canUpdateWAddr = in.aw.valid && in.w.valid
  val wAddrReg = RegEnable(
    wAddrComb,
    canUpdateWAddr
  )
  val wAddr = Mux(canUpdateWAddr, wAddrComb, wAddrReg)

  when (isMemAddr(wAddr)) {
    mem.aw :<>= in.aw
    mem.w :<>= in.w
  } .elsewhen (isUartAddr(wAddr)) {
    uart.aw :<>= in.aw
    uart.w :<>= in.w
  }

  when (isMemAddr(wAddrReg)) {
    in.b :<>= mem.b
  } .elsewhen (isUartAddr(wAddrReg)) {
    in.b :<>= uart.b
  } .otherwise {
    in.b.bits.resp := "b11".U
  }
}

class UartDevice extends Module {
  val in = IO(Flipped(new Axi4LiteIO))

  0.U.asTypeOf(chiselTypeOf(in.ar)) :>= in.ar
  in.r :<= 0.U.asTypeOf(chiselTypeOf(in.r))

  val sIdle :: sWaitResp :: Nil = Enum(2)
  val state = RegInit(sIdle)
  state := MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(in.aw.valid && in.w.valid, sWaitResp, sIdle),
      sWaitResp -> Mux(in.b.ready, sIdle, sWaitResp)
    )
  )
  val inputValid = state === sIdle && in.aw.valid && in.w.valid
  in.aw.ready := inputValid
  in.w.ready := inputValid
  in.b.valid := state === sWaitResp
  in.b.bits.resp := 0.U
  when(inputValid) {
    printf("[sim] %c\n", in.w.bits.data(7, 0))
  }
}
