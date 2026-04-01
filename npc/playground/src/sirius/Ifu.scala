package sirius

import chisel3._
import chisel3.util._

class Icache(lineNum: BigInt, lineByte: BigInt, addrByte: BigInt) extends Module {
  require(lineNum > 0 && lineNum.bitCount == 1)
  require(lineByte > 0 && lineByte.bitCount == 1)
  require(lineByte == 4)

  val io = IO(new Bundle {
    val cached = Flipped(new Axi4IO)
    val mem = new Axi4IO
  })

  assert(!io.cached.aw.valid && !io.cached.w.valid)

  class AddrLine extends Bundle {
    val tag = UInt((addrByte * 8 - log2Ceil(lineByte) - log2Ceil(lineNum)).toInt.W)
    val idx = UInt(log2Ceil(lineNum).W)
    val off = UInt(log2Ceil(lineByte).W)
  }
  val rAddrReg = RegEnable(io.cached.ar.bits.addr, io.cached.ar.valid)
  val rAddrLine = rAddrReg.asTypeOf(new AddrLine)

  class Line extends Bundle {
    val tag = UInt((addrByte * 8 - log2Ceil(lineByte) - log2Ceil(lineNum)).toInt.W)
    val data = UInt((lineByte * 8).toInt.W)
  }
  val cache = Mem(lineNum.toInt, new Line)
  val validReg = RegInit(0.U.asTypeOf(Vec(lineNum.toInt, Bool())))
  val line = cache.read(rAddrLine.idx)
  val lineValid = validReg(rAddrLine.idx)
  val hit = lineValid && line.tag === rAddrLine.tag

  val sIdle :: sHit :: sMiss :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val nextState = MuxLookup(state, sIdle)(Seq(
    sIdle -> Mux(io.cached.ar.valid, Mux(hit, sHit, sMiss), sIdle),
    sHit -> Mux(io.cached.r.fire, sIdle, sHit),
    sMiss -> Mux(io.mem.r.fire, sIdle, sMiss)
    ))
  state := nextState

  io.cached :<= 0.U.asTypeOf(chiselTypeOf(io.cached))
  0.U.asTypeOf(chiselTypeOf(io.mem)) :>= io.mem
  io.cached.ar.ready := state === sHit
  io.cached.r.valid := state === sHit
  io.cached.r.bits.data := line.data
  when (state === sMiss) {
    io.mem :<>= io.cached
  }
  when (state === sMiss && io.mem.r.fire) {
    lineValid := true.B
    line.tag := rAddrLine.tag
    line.data := io.mem.r.bits.data
  }
}

class Ifu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val exte = IO(new Bundle {
    val pcReg = new IfuToPcRegIO
    val mem = new Axi4IO
  })
  val out = IO(Decoupled(new IfuToIduIO))
  val debug = Option.when(cfg.isDebug)(IO(Output(UInt(cfg.xlen.W))))

  val pc = exte.pcReg.pc

  out.bits.ifuPayload.trap := 0.U.asTypeOf(
    chiselTypeOf(out.bits.ifuPayload.trap)
  )

  val outBits = out.bits

  val sIdle :: sWaitResp :: sKeepData :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val canValid = RegNext(RegNext(!reset.asBool))

  val nextState = MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(
        icache.io.cached.ar.fire,
        Mux(icache.io.cached.r.valid, Mux(out.fire, sIdle, sKeepData), sWaitResp),
        sIdle
      ),
      sWaitResp -> Mux(
        icache.io.cached.r.valid,
        Mux(out.fire, sIdle, sKeepData),
        sWaitResp
      ),
      sKeepData -> Mux(out.fire, sIdle, sKeepData)
    )
  )
  state := nextState

  // exte.mem :<= 0.U.asTypeOf(new Axi4IO)
  // exte.mem.ar.bits.size := "b010".U
  // assert(!exte.mem.aw.valid && !exte.mem.w.valid && !exte.mem.b.valid)
  // exte.mem.ar.valid := state === sIdle && canValid
  // exte.mem.ar.bits.addr := pc
  // exte.mem.r.ready := true.B
  val icache = Module(new Icache(lineNum = 16, lineByte = 4, addrByte = 4))
  exte.mem :<>= icache.io.mem
  icache.io.cached :<= 0.U.asTypeOf(new Axi4IO)
  icache.io.cached.ar.bits.size := "b010".U
  assert(!icache.io.cached.aw.valid && !icache.io.cached.w.valid && !icache.io.cached.b.valid)
  icache.io.cached.ar.valid := state === sIdle && canValid
  icache.io.cached.ar.bits.addr := pc
  icache.io.cached.r.ready := true.B

  val dataReg = RegEnable(
    icache.io.cached.r.bits.data,
    state =/= sKeepData && nextState === sKeepData
  )
  // out.valid := (state === sWaitResp && exte.mem.r.valid) || state === sKeepData
  // out.valid := state === sKeepData
  out.valid := icache.io.cached.r.valid || state === sKeepData
  // outBits.ifuPayload.ifu.inst := dataReg
  outBits.ifuPayload.ifu.inst := Mux(state === sKeepData, dataReg, icache.io.cached.r.bits.data)
  outBits.ifuPayload.ifu.pc := pc

  if (cfg.isDebug) {
    debug.get := outBits.ifuPayload.ifu.inst
  }
  PerfWhen(
    "instFetch",
    exte.mem.r.fire,
    out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
  )
  if (cfg.perf) {
    val isMemBusy = RegInit(false.B)
    when (!isMemBusy && exte.mem.ar.valid && !exte.mem.r.valid) {
      isMemBusy := true.B
    }
    when (isMemBusy && exte.mem.r.valid) {
      isMemBusy := false.B
    }
    PerfWhen(
      "waitReadCyc",
      isMemBusy,
      out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
    )
    PerfWhen(
      "keepDataCyc",
      out.valid && !out.ready,
      out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
    )
  }
}
