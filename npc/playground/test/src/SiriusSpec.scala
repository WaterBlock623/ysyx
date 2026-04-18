package sirius

import chisel3._
import chisel3.util._
import formal.Formal
import formal.ModuleWithInitReset
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

// class IcacheTest extends ModuleWithInitReset {
//   val io = IO(new Bundle {
//     val req = Flipped(new Axi4IO)
//     val block = Input(Bool())
//   })
//
//   val busy = RegInit(false.B)
//   val arvalid = RegInit(false.B)
//   val addrReg = RegEnable(io.req.ar.bits.addr, !busy && io.req.ar.valid)
//   val memSize = 128 // byte
//   // val mem = dontTouch(Reg(Vec(memSize / 4, UInt(32.W))))
//   val random = new Random()
//   val mem = dontTouch(
//     RegInit(VecInit(Seq.fill(memSize / 4)(BigInt(32, random).U(32.W))))
//   )
//   val dut = Module(
//     new Icache(
//       setNum = 2,
//       wayNum = 8,
//       wayByte = 8,
//       busByte = 4
//     )
//   )
//
//   when(!busy && io.req.ar.valid) {
//     busy := true.B
//     arvalid := true.B
//   }
//   when(busy && io.req.r.fire) {
//     busy := false.B
//   }
//   when(arvalid && dut.io.cached.ar.fire) {
//     arvalid := false.B
//   }
//
//   dut.io.flush := false.B
//   dut.io.cached :<>= io.req
//   dut.io.cached.ar.valid := arvalid
//   dut.io.cached.ar.bits.addr := addrReg
//   dut.io.cached.aw.valid := false.B
//   dut.io.cached.w.valid := false.B
//   0.U.asTypeOf(chiselTypeOf(dut.io.mem)) :>= dut.io.mem
//   dut.io.mem.ar.ready := true.B
//   val memRValid = RegInit(false.B)
//   val memRAddr = Reg(chiselTypeOf(dut.io.mem.ar.bits.addr))
//   when (dut.io.mem.ar.fire) {
//     memRValid := true.B
//     memRAddr := dut.io.mem.ar.bits.addr
//   }
//   when (dut.io.mem.r.fire) {
//     memRValid := false.B
//   }
//   dut.io.mem.r.valid := memRValid
//   dut.io.mem.r.bits.data := mem(memRAddr >> 2)
//
//   val dutRData = dut.io.cached.r.bits.data
//   val refRData = WireDefault(mem(addrReg >> 2))
//   dontTouch(refRData)
//   when(dut.io.cached.r.valid) {
//     assert(dutRData === refRData)
//     // assert(dutRData === 0.U)
//   }
// }

class AxiReadConstraint extends Module {
  val io = IO(new Bundle {
    val ar = Flipped((new Axi4IO).ar)
  })
  0.U.asTypeOf(chiselTypeOf(io.ar)) :>= io.ar

  assume(io.ar.bits.id === 0.U)
  assume(io.ar.bits.len === 0.U)
  assume(io.ar.bits.burst === 1.U)
  assume(io.ar.bits.size === 2.U)

  val sizeBytes = 1.U << io.ar.bits.size
  assume((io.ar.bits.addr & (sizeBytes - 1.U)) === 0.U)

  // val ar_fire = io.ar.valid && io.ar.ready

  val valid_d = RegNext(io.ar.valid, init=false.B)
  val ready_d = RegNext(io.ar.ready, init=false.B)
  val bits_d  = RegNext(io.ar.bits)

  when (valid_d && !ready_d) {
    assume(io.ar.valid)
    assume(io.ar.bits === bits_d)
  }
}

class IcacheTest extends ModuleWithInitReset {
  val io = IO(new Bundle {
    val req = Flipped(new Axi4IO)
  })

  val memSize = 128
  val random = new Random()
  val mem = RegInit(VecInit(Seq.fill(memSize / 4)(BigInt(32, random).U(32.W))))

  val dut = Module(new Icache(setNum = 2, wayNum = 8, wayByte = 8, busByte = 4))

  dut.io.flush := false.B
  dut.io.cached :<>= io.req
  // dut.io.cached.aw.valid := false.B
  // dut.io.cached.w.valid := false.B
  val arC = Module(new AxiReadConstraint)
  arC.io.ar :<= io.req.ar
  assume(!io.req.aw.valid)
  assume(!io.req.w.valid)
  0.U.asTypeOf(chiselTypeOf(dut.io.mem)) :>= dut.io.mem

  val s_mem_idle :: s_mem_resp :: Nil = Enum(2)
  val memState = RegInit(s_mem_idle)
  
  val memArReg = Reg(chiselTypeOf(dut.io.mem.ar.bits))
  val burstCnt = RegInit(0.U(8.W))
  
  dut.io.mem.ar.ready := memState === s_mem_idle
  
  when(dut.io.mem.ar.fire) {
    memArReg := dut.io.mem.ar.bits
    burstCnt := 0.U
    memState := s_mem_resp
  }

  val currentAddr = Wire(UInt(32.W))
  val isWrap = memArReg.burst === 2.U
  val wrapMask = (memArReg.len + 1.U) << 2
  val baseAddr = memArReg.addr & ~(wrapMask - 1.U)
  
  val incrementedAddr = memArReg.addr + (burstCnt << 2)
  val wrappedAddr = baseAddr | (incrementedAddr & (wrapMask - 1.U))
  
  currentAddr := Mux(isWrap, wrappedAddr, incrementedAddr)

  dut.io.mem.r.valid := memState === s_mem_resp
  dut.io.mem.r.bits.data := mem(currentAddr >> 2)
  dut.io.mem.r.bits.last := burstCnt === memArReg.len
  dut.io.mem.r.bits.resp := 0.U
  dut.io.mem.r.bits.id := memArReg.id

  when(dut.io.mem.r.fire) {
    if (dut.burstTimes > 1) {
      burstCnt := burstCnt + 1.U
      when(dut.io.mem.r.bits.last) {
        memState := s_mem_idle
      }
    } else {
      memState := s_mem_idle
    }
  }

  val addrReg = RegEnable(io.req.ar.bits.addr, io.req.ar.fire)
  val refRData = WireDefault(mem(addrReg >> 2))
  dontTouch(refRData)
  when(io.req.r.valid) {
    assert(io.req.r.bits.data === refRData)
  }
}

class FormalTest extends AnyFlatSpec {
  "icache" should "pass" in {
    Formal.verify(new IcacheTest, "IcacheTest", 30, 10)
  }
}
