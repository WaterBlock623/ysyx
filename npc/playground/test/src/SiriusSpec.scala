package sirius

import chisel3._
import chisel3.util._
import cpuutil.Formal
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

class ResetN(n: Int) extends ExtModule {
  val clock = IO(Input(Bool()))
  val reset_out = IO(Output(Bool()))
  setInline(
    "ResetN.v",
    s"""
       |module ResetN (
       |  input  wire clock,
       |  output reg  reset_out
       |);
       |
       |  reg [$$clog2(${n}+1)-1:0] cnt = 0;
       |
       |  always @(posedge clock) begin
       |    if (cnt < ${n}) begin
       |      cnt <= cnt + 1;
       |    end
       |  end
       |
       |  assign reset_out = cnt < ${n};
       |
       |endmodule
  """.stripMargin
  )
}

class IcacheTest extends Module {
  val io = IO(new Bundle {
    val req = Flipped(new Axi4IO)
    val block = Input(Bool())
  })

  val rst = Module(new ResetN(1))
  rst.clock := clock.asBool
  withReset(rst.reset_out) {
    val busy = RegInit(false.B)
    val arvalid = RegInit(false.B)
    val addrReg = RegEnable(io.req.ar.bits.addr, !busy && io.req.ar.valid)
    val memSize = 128 // byte
    // val mem = dontTouch(Reg(Vec(memSize / 4, UInt(32.W))))
    val random = new Random()
    val mem = dontTouch(
      RegInit(VecInit(Seq.fill(memSize / 4)(BigInt(32, random).U(32.W))))
    )
    val dut = Module(
      new Icache(
        lineNum = 16,
        lineByte = 4,
        addrByte = 4
      )
    )

    when(!busy && io.req.ar.valid) {
      busy := true.B
      arvalid := true.B
    }
    when(busy && io.req.r.fire) {
      busy := false.B
    }
    when(arvalid && dut.io.cached.ar.fire) {
      arvalid := false.B
    }

    dut.io.cached :<>= io.req
    dut.io.cached.ar.valid := arvalid
    dut.io.cached.ar.bits.addr := addrReg
    dut.io.cached.aw.valid := false.B
    dut.io.cached.w.valid := false.B
    0.U.asTypeOf(chiselTypeOf(dut.io.mem)) :>= dut.io.mem
    dut.io.mem.r.bits.data := mem(dut.io.mem.ar.bits.addr >> 2)
    dut.io.mem.ar.ready := true.B
    dut.io.mem.r.valid := true.B

    val dutRData = dut.io.cached.r.bits.data
    val refRData = WireDefault(mem(addrReg >> 2))
    dontTouch(refRData)
    when(dut.io.cached.r.valid) {
      assert(dutRData === refRData)
      // assert(dutRData === 0.U)
    }
  }
}

class FormalTest extends AnyFlatSpec {
  "icache" should "pass" in {
    Formal.verify(new IcacheTest, "IcacheTest", 22)
  }
}
