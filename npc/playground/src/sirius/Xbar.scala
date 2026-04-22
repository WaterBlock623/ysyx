package sirius

import chisel3._
import chisel3.util._

class AutoLocker(nPort: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(nPort.W))
    val out = Output(UInt(nPort.W))
  })

  val prevOut = RegNext(io.out, 0.U)
  val keeping = prevOut & io.in
  val hasKeeping = keeping.orR
  io.out := Mux(hasKeeping, keeping, io.in)
}

class ArbiterAutoLock[T <: Data](val gen: T, val n: Int) extends Module {
  val io = IO(new ArbiterIO(gen, n))

  val testaa = 1
  val arbiter = Module(new Arbiter(gen, n) {
    for ((in, g) <- this.io.in.zip(this.grant))
      in.ready := g && in.valid && this.io.out.ready
  })
  dontTouch(arbiter.io)
  val autoLocker = Module(new AutoLocker(n))

  val valids = VecInit(io.in.map(_.valid)).asUInt
  autoLocker.io.in := valids

  io :<>= arbiter.io
  for (i <- 0 until n) {
    arbiter.io.in(i).valid := autoLocker.io.out(i)
  }
}

class Xbar(
  nMasters: Int = 2,
  nSlaves:  Int = 2,
  routeFn:  Seq[UInt => Bool],
  busWidth: Int = 32,
  idWidth:  Int = 4)
    extends Module {

  require(routeFn.length == nSlaves)
  val masterIdBits = log2Ceil(nMasters)
  require(masterIdBits < idWidth, "ID width not enough")

  val lowIdBits = idWidth - masterIdBits

  val io = IO(new Bundle {
    val in = Flipped(Vec(nMasters, new Axi4IO(busWidth)))
    val out = Vec(nSlaves, new Axi4IO(busWidth))
  })

  for (m <- 0 until nMasters) {
    when (io.in(m).aw.fire) {
      assert(io.in(m).aw.bits.id(idWidth - 1, lowIdBits) === 0.U)
    }
    when (io.in(m).ar.fire) {
      assert(io.in(m).ar.bits.id(idWidth - 1, lowIdBits) === 0.U)
    }
  }

  def decode(addr: UInt): Vec[Bool] = {
    VecInit(routeFn.map(f => f(addr)))
  }

  io.out :<= 0.U.asTypeOf(chiselTypeOf(io.out))
  0.U.asTypeOf(chiselTypeOf(io.in)) :>= io.in

  // AW W
  val awReady = Wire(Vec(nMasters, Vec(nSlaves, Bool())))
  val wReady = Wire(Vec(nMasters, Vec(nSlaves, Bool())))
  for (s <- 0 until nSlaves) {
    val awArb = Module(new ArbiterAutoLock(chiselTypeOf(io.in(0).aw.bits), nMasters))
    val wArb = Module(new ArbiterAutoLock(chiselTypeOf(io.in(0).w.bits), nMasters))

    for (m <- 0 until nMasters) {
      val sel = decode(io.in(m).aw.bits.addr)(s)
      val fireCond = io.in(m).aw.valid && io.in(m).w.valid && sel

      awArb.io.in(m).valid := fireCond
      awArb.io.in(m).bits := io.in(m).aw.bits
      awArb.io.in(m).bits.id :=
        m.U(masterIdBits.W) ## io.in(m).aw.bits.id(lowIdBits - 1, 0)

      wArb.io.in(m).valid := fireCond
      wArb.io.in(m).bits := io.in(m).w.bits

      awReady(m)(s) := awArb.io.in(m).ready && fireCond
      wReady(m)(s) := wArb.io.in(m).ready && fireCond
    }

    io.out(s).aw :<>= awArb.io.out
    io.out(s).w :<>= wArb.io.out
  }
  for (m <- 0 until nMasters) {
    io.in(m).aw.ready := awReady(m).reduce(_ || _)
    io.in(m).w.ready := wReady(m).reduce(_ || _)
  }

  // AR
  val arReady = Wire(Vec(nMasters, Vec(nSlaves, Bool())))
  for (s <- 0 until nSlaves) {
    val arArb = Module(new ArbiterAutoLock(chiselTypeOf(io.in(0).ar.bits), nMasters))

    for (m <- 0 until nMasters) {
      val sel = decode(io.in(m).ar.bits.addr)(s)

      arArb.io.in(m).valid := io.in(m).ar.valid && sel
      arArb.io.in(m).bits := io.in(m).ar.bits
      arArb.io.in(m).bits.id :=
        m.U(masterIdBits.W) ## io.in(m).ar.bits.id(lowIdBits - 1, 0)

      arReady(m)(s) := arArb.io.in(m).ready && sel
    }

    io.out(s).ar :<>= arArb.io.out
  }
  for (m <- 0 until nMasters) {
    io.in(m).ar.ready := arReady(m).reduce(_ || _)
  }

  // B
  for (s <- 0 until nSlaves) {
    val bid = io.out(s).b.bits.id

    val masterSel = bid(idWidth - 1, lowIdBits)
    val realId = bid(lowIdBits - 1, 0)

    for (m <- 0 until nMasters) {
      when(masterSel === m.U && io.out(s).b.valid) {
        io.in(m).b.valid := io.out(s).b.valid
        io.in(m).b.bits := io.out(s).b.bits
        io.in(m).b.bits.id := realId

        io.out(s).b.ready := io.in(m).b.ready
      }
    }
  }

  // R
  for (s <- 0 until nSlaves) {
    val rid = io.out(s).r.bits.id

    val masterSel = rid(idWidth - 1, lowIdBits)
    val realId = rid(lowIdBits - 1, 0)

    for (m <- 0 until nMasters) {
      when(masterSel === m.U && io.out(s).r.valid) {
        io.in(m).r.valid := io.out(s).r.valid
        io.in(m).r.bits := io.out(s).r.bits
        io.in(m).r.bits.id := realId

        io.out(s).r.ready := io.in(m).r.ready
      }
    }
  }
}

// class MemBusArbiter(
//   implicit private val cfg: CoreConfig)
//     extends Module {
//   val in = IO(Vec(2, Flipped(new Axi4IO)))
//   val out = IO(new Axi4IO)
//
//   val ifu = in(0)
//   val lsu = in(1)
//
//   val sIdle :: sIfu :: sLsu :: Nil = Enum(3)
//   val state = RegInit(sIdle)
//
//   val ifuValid = ifu.ar.valid
//   val ifuReady = ifu.r.fire && ifu.r.bits.last
//   val lsuValid = lsu.ar.valid || lsu.aw.valid || lsu.w.valid
//   val lsuReady = (lsu.r.fire && lsu.r.bits.last) || lsu.b.fire
//
//   state := MuxLookup(state, sIdle)(
//     Seq(
//       sIdle -> MuxCase(
//         sIdle,
//         Seq(
//           ifuValid -> sIfu,
//           lsuValid -> sLsu
//         )
//       ),
//       sIfu -> Mux(ifuReady, sIdle, sIfu),
//       sLsu -> Mux(lsuReady, sIdle, sLsu)
//     )
//   )
//
//   0.U.asTypeOf(chiselTypeOf(ifu)) :>= ifu
//   0.U.asTypeOf(chiselTypeOf(lsu)) :>= lsu
//   out :<= 0.U.asTypeOf(chiselTypeOf(out))
//
//   switch(state) {
//     is(sIdle) {
//       when(ifuValid) {
//         out :<>= ifu
//       }.elsewhen(lsuValid) {
//         out :<>= lsu
//       }
//     }
//     is(sIfu) {
//       out :<>= ifu
//     }
//     is(sLsu) {
//       out :<>= lsu
//     }
//   }
//   // out.w.bits.last := true.B
// }
//
// class Xbar(
//   implicit private val cfg: CoreConfig)
//     extends Module {
//   val in = IO(Flipped(new Axi4IO))
//   val out = IO(Vec(2, new Axi4IO))
//
//   val soc = out(0)
//   val clint = out(1)
//   def isClintAddr(addr: UInt): Bool = addr >= "h02000000".U && addr < "h02010000".U
//
//   out :<= 0.U.asTypeOf(chiselTypeOf(out))
//   0.U.asTypeOf(chiselTypeOf(in)) :>= in
//
//   val canUpdateRAddr = in.ar.valid
//   val rAddrReg = Reg(chiselTypeOf(in.ar.bits.addr))
//   val rAddr = WireDefault(rAddrReg)
//   rAddrReg := rAddr
//   when (canUpdateRAddr) {
//     rAddr := in.ar.bits.addr
//   }
//
//   when(isClintAddr(rAddr)) {
//     clint.ar :<>= in.ar
//   } .otherwise {
//     soc.ar :<>= in.ar
//   }
//
//   when(isClintAddr(rAddrReg)) {
//     in.r :<>= clint.r
//   } .otherwise {
//     in.r :<>= soc.r
//   }
//
//   val canUpdateWAddr = in.aw.valid && in.w.valid
//   val wAddrReg = Reg(chiselTypeOf(in.aw.bits.addr))
//   val wAddr = WireDefault(wAddrReg)
//   wAddrReg := wAddr
//   when (canUpdateWAddr) {
//     wAddr := in.aw.bits.addr
//   }
//
//   when(isClintAddr(wAddr)) {
//     clint.aw :<>= in.aw
//     clint.w :<>= in.w
//   }.otherwise {
//     soc.aw :<>= in.aw
//     soc.w :<>= in.w
//   }
//
//   when(isClintAddr(wAddrReg)) {
//     in.b :<>= clint.b
//   }.otherwise {
//     in.b :<>= soc.b
//   }
// }

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
  when(in.ar.valid) {
    assert(in.ar.bits.len === 0.U)
  }
  in.r.bits.last := true.B
  val idReg = Reg(chiselTypeOf(in.ar.bits.id))
  when(in.ar.fire) {
    idReg := in.ar.bits.id
  }
  in.r.bits.id := idReg

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

  in.r.bits.data := MuxLookup(state, 0.U)(
    Seq(
      sMtimeLo -> mtimeReg(31, 0),
      sMtimeHi -> mtimeReg(63, 32)
    )
  )
}
