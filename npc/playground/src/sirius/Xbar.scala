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

  switch (state) {
    is (sIdle) {
      when (ifuValid) {
        out :<>= ifu
      } .elsewhen (lsuValid) {
        out :<>= lsu
      }
    }
    is (sIfu) {
      out :<>= ifu
    }
    is (sLsu) {
      out :<>= lsu
    }
  }
}
