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
  out.w.bits.last := true.B
}

class Xbar(
  implicit private val cfg: CoreConfig)
    extends Module {
  val in = IO(Flipped(new Axi4IO))
  val out = IO(Vec(2, new Axi4IO))

  val soc = out(0)
  val clint = out(1)
  def isClintAddr(addr: UInt): Bool = addr >= "h02000000".U && addr < "h02010000".U

  out :<= 0.U.asTypeOf(chiselTypeOf(out))
  0.U.asTypeOf(chiselTypeOf(in)) :>= in

  val canUpdateRAddr = in.ar.valid
  val rAddrReg = Reg(chiselTypeOf(in.ar.bits.addr))
  val rAddr = WireDefault(rAddrReg)
  rAddrReg := rAddr
  when (canUpdateRAddr) {
    rAddr := in.ar.bits.addr
  }

  when(isClintAddr(rAddr)) {
    clint.ar :<>= in.ar
  } .otherwise {
    soc.ar :<>= in.ar
  }

  when(isClintAddr(rAddrReg)) {
    in.r :<>= clint.r
  } .otherwise {
    in.r :<>= soc.r
  }

  val canUpdateWAddr = in.aw.valid && in.w.valid
  val wAddrReg = Reg(chiselTypeOf(in.aw.bits.addr))
  val wAddr = WireDefault(wAddrReg)
  wAddrReg := wAddr
  when (canUpdateWAddr) {
    wAddr := in.aw.bits.addr
  }

  when(isClintAddr(wAddr)) {
    clint.aw :<>= in.aw
    clint.w :<>= in.w
  }.otherwise {
    soc.aw :<>= in.aw
    soc.w :<>= in.w
  }

  when(isClintAddr(wAddrReg)) {
    in.b :<>= clint.b
  }.otherwise {
    in.b :<>= soc.b
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
