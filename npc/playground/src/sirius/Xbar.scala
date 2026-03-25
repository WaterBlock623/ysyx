package sirius

import chisel3._
import chisel3.util._
import chisel3.SpecifiedDirection.Flip

class MemBusArbiter(
  implicit private val cfg: CoreConfig)
    extends Module {
  val in = IO(Vec(2, Flipped(new Axi4IO)))
  val out = IO(new Axi4IO)

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
  val in = IO(Flipped(new Axi4IO))
  val out = IO(Vec(2, new Axi4IO))

  val mem = out(0)
  // def isMemAddr(addr: UInt): Bool = addr >= "h2000_0000".U && addr <= "h2000_0fff".U
  // val uart = out(1)
  // def isUartAddr(addr: UInt): Bool = addr === "h10000000".U
  val clint = out(1)
  def isClintAddr(addr: UInt): Bool = addr >= "h02000000".U && addr < "h02010000".U

  out :<= 0.U.asTypeOf(chiselTypeOf(out))
  0.U.asTypeOf(chiselTypeOf(in)) :>= in

  val rAddrComb = in.ar.bits.addr
  val canUpdateRAddr = in.ar.valid
  val rAddrReg = RegEnable(
    rAddrComb,
    canUpdateRAddr
  )

  // when(isMemAddr(rAddrComb)) {
  //   mem.ar :<>= in.ar
  // // }.elsewhen(isUartAddr(rAddrComb)) {
  // //   uart.ar :<>= in.ar
  // }.elsewhen(isClintAddr(rAddrComb)) {
  //   clint.ar :<>= in.ar
  // }.otherwise {
  //   mem.ar :<>= in.ar // test
  // }
  when(isClintAddr(rAddrComb)) {
    clint.ar :<>= in.ar
  } .otherwise {
    mem.ar :<>= in.ar
  }

  // when(isMemAddr(rAddrReg)) {
  //   in.r :<>= mem.r
  // // }.elsewhen(isUartAddr(rAddrReg)) {
  // //   in.r :<>= uart.r
  // }.elsewhen(isClintAddr(rAddrReg)) {
  //   in.r :<>= clint.r
  // }.otherwise {
  //   // in.r.bits.resp := "b11".U
  //   in.r :<>= mem.r // test
  // }
  when(isClintAddr(rAddrReg)) {
    in.r :<>= clint.r
  } .otherwise {
    in.r :<>= mem.r
  }

  val wAddrComb = in.aw.bits.addr
  val canUpdateWAddr = in.aw.valid && in.w.valid
  val wAddrReg = RegEnable(
    wAddrComb,
    canUpdateWAddr
  )
  val wAddr = Mux(canUpdateWAddr, wAddrComb, wAddrReg)

  // when(isMemAddr(wAddr)) {
  //   mem.aw :<>= in.aw
  //   mem.w :<>= in.w
  // // }.elsewhen(isUartAddr(wAddr)) {
  // //   uart.aw :<>= in.aw
  // //   uart.w :<>= in.w
  // }.elsewhen(isClintAddr(wAddr)) {
  //   clint.aw :<>= in.aw
  //   clint.w :<>= in.w
  // }.otherwise {
  //   // test
  //   mem.aw :<>= in.aw
  //   mem.w :<>= in.w
  // }
  when(isClintAddr(wAddr)) {
    clint.aw :<>= in.aw
    clint.w :<>= in.w
  }.otherwise {
    mem.aw :<>= in.aw
    mem.w :<>= in.w
  }

  // when(isMemAddr(wAddrReg)) {
  //   in.b :<>= mem.b
  // // }.elsewhen(isUartAddr(wAddrReg)) {
  // //   in.b :<>= uart.b
  // }.elsewhen(isClintAddr(wAddrReg)) {
  //   in.b :<>= clint.b
  // }.otherwise {
  //   // in.b.bits.resp := "b11".U
  //   in.b :<>= mem.b // test
  // }
  when(isClintAddr(wAddrReg)) {
    in.b :<>= clint.b
  }.otherwise {
    in.b :<>= mem.b
  }
}

class UartDevice extends Module {
  val in = IO(Flipped(new Axi4IO))

  0.U.asTypeOf(chiselTypeOf(in.ar)) :>= in.ar
  in.r :<= 0.U.asTypeOf(chiselTypeOf(in.r))
  assert(!in.ar.valid && !in.r.valid)

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
    printf("%c", in.w.bits.data(7, 0))
  }
}

class ClintDevice extends Module {
  val in = IO(Flipped(new Axi4IO))

  0.U.asTypeOf(chiselTypeOf(in)) :>= in
  assert(!in.aw.valid && !in.w.valid)

  val sIdle :: sMtimeLo :: sMtimeHi :: sError :: Nil = Enum(4)
  val state = RegInit(sIdle)
  val nextState = MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(
        in.ar.fire,
        MuxCase(
          sError,
          Seq(
            (in.ar.bits.addr === "h02000000".U) -> sMtimeLo,
            (in.ar.bits.addr === "h02000004".U) -> sMtimeHi
          )
        ),
        sIdle
      ),
      sMtimeLo -> Mux(in.r.fire, sIdle, sMtimeLo),
      sMtimeHi -> Mux(in.r.fire, sIdle, sMtimeHi)
    )
  )
  state := nextState
  assert(state =/= sError)

  in.ar.ready := state === sIdle && in.ar.valid
  in.r.valid := state =/= sIdle

  val mtimeReg = RegInit(0.U(64.W))
  mtimeReg := mtimeReg + 1.U

  in.r.bits.data := MuxLookup(state, 0.U)(Seq(
    sMtimeLo -> mtimeReg(31, 0),
    sMtimeHi -> mtimeReg(63, 32)
    ))
}
