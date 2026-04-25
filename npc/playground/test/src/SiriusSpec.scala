package sirius

import chisel3._
import chisel3.util._
import formal.Formal
import formal.ModuleWithInitReset
import org.scalatest.flatspec.AnyFlatSpec
import scala.util.Random

object IcacheMasterConstraint {
  def apply(io: IcacheIO): IcacheIO = {
    assume(io.ar.bits.addr(1, 0) === 0.U)

    val validReg = RegNext(io.ar.valid, init = false.B)
    val readyReg = RegNext(io.ar.ready, init = false.B)
    val bitsReg = RegNext(io.ar.bits)

    when(validReg && !readyReg) {
      assume(io.ar.valid)
      assume(io.ar.bits === bitsReg)
    }
    io
  }
}

class IcacheTest extends ModuleWithInitReset {
  val io = IO(new Bundle {
    val req = Flipped(new IcacheIO)
  })
  IcacheMasterConstraint(io.req)

  val memSize = 128
  val random = new Random()
  val mem = RegInit(VecInit(Seq.fill(memSize / 4)(BigInt(32, random).U(32.W))))

  val dut = Module(new Icache(setNum = 2, wayNum = 8, wayByte = 8, busByte = 4))

  dut.io.cached :<>= io.req
  assume(!io.req.fencei)
  assume(!io.req.abort)

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

object AxiSlaveConstraint {
  def apply[T <: Axi4IO](axi: T): T = {
    // when(axi.ar.valid) {
    //   assert(axi.ar.bits.len === 0.U)
    // }
    when(axi.aw.valid) {
      assert(axi.aw.bits.len === 0.U)
    }

    def keepBoolUntil(valid: Bool, reset: Bool): Bool = {
      val validReg = RegInit(false.B)
      when(valid) {
        validReg := true.B
      }
      when(reset) {
        validReg := false.B
      }
      valid || validReg
    }

    // R
    val arBits = RegEnable(axi.ar.bits, axi.ar.fire)
    val rBurstCnt = Counter(0 until 8, axi.r.fire, axi.ar.fire)
    when(axi.r.valid) {
      assume(axi.r.bits.id === arBits.id)
      when(rBurstCnt._1 === arBits.len) {
        assume(axi.r.bits.last)
      }.otherwise {
        assume(!axi.r.bits.last)
      }
    }
    val arFired = keepBoolUntil(axi.ar.fire, rBurstCnt._1 === arBits.len)
    when(!arFired) {
      assume(!axi.r.valid)
    }

    // W
    val wId = Reg(chiselTypeOf(axi.aw.bits.id))
    when(axi.aw.fire) {
      wId := axi.aw.bits.id
    }
    when(axi.w.valid) {
      assert(axi.w.bits.last)
    }
    when(axi.b.valid) {
      assume(axi.b.bits.id === wId)
    }
    val awFired = keepBoolUntil(axi.aw.fire, axi.b.fire)
    val wFired = keepBoolUntil(axi.w.fire, axi.b.fire)
    when(!awFired || !wFired) {
      assume(!axi.b.valid)
    }

    axi
  }
}

class BasicCoreTest extends ModuleWithInitReset {
  val io = IO(new Bundle {
    val imem = new Axi4IO
    val dmem = new Axi4IO
  })
  AxiSlaveConstraint(io.imem)
  AxiSlaveConstraint(io.dmem)
  val workSpaceRoot = os.Path(sys.env("WORKSPACE_ROOT_DIR"))
  val rvOpCodesPath = workSpaceRoot / "rvdecoderdb" / "riscv-opcodes"
  val cfg = CoreConfig.default.copy(
    isDebug = false,
    perf = false,
    formal = true,
    rvOpCodesPath = rvOpCodesPath
  )
  val ucfg = UnitConfig()
  val basicCore = Module(new BasicCore()(cfg, ucfg))
  io.imem :<>= basicCore.io.axiIfu
  io.dmem :<>= basicCore.io.axiLsu
}

class SiriusSpec extends AnyFlatSpec {
  "icache" should "pass" in {
    Formal.verify(new IcacheTest, "IcacheTest", 30, 10)
  }
  "basicCore" should "pass" in {
    Formal.verify(new BasicCoreTest, "BasicCoreTest", depth = 50, skip = 0, append = 0)
  }
}
