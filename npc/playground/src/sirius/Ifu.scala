package sirius

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import scala.collection.immutable.NumericRange

class Icache(
  lineNum:   BigInt,
  lineByte:  BigInt,
  busByte:   BigInt,
  whiteList: Option[NumericRange[BigInt]] = None)
    extends Module {
  require(lineNum > 0 && lineNum.bitCount == 1)
  require(lineByte >= busByte && lineByte.bitCount == 1)
  require(busByte > 0 && busByte.bitCount == 1)
  val burstTimes = lineByte / busByte

  val io = IO(new Bundle {
    val cached = Flipped(new Axi4IO)
    val mem = new Axi4IO
  })

  assert(!io.cached.aw.valid && !io.cached.w.valid)

  // Addr
  class AddrLine extends Bundle {
    val tag = UInt(
      (busByte * 8 - log2Ceil(lineByte) - log2Ceil(lineNum)).toInt.W
    )
    val idx = UInt(log2Ceil(lineNum).W)
    val off = UInt(log2Ceil(lineByte).W)
  }
  val rAddrReg = RegEnable(io.cached.ar.bits.addr, io.cached.ar.valid)
  val rAddrLine = rAddrReg.asTypeOf(new AddrLine)
  val inWhiteList = if (whiteList.isDefined) {
    rAddrReg >= whiteList.get.start.U && rAddrReg < whiteList.get.end.U
  } else { true.B }

  // Cache
  class Line extends Bundle {
    val tag = UInt(
      (busByte * 8 - log2Ceil(lineByte) - log2Ceil(lineNum)).toInt.W
    )
    val data = Vec(burstTimes.toInt, UInt((busByte * 8).toInt.W))
  }
  val cache = Mem(lineNum.toInt, new Line)
  val validReg = RegInit(0.U.asTypeOf(Vec(lineNum.toInt, Bool())))
  val line = cache.read(rAddrLine.idx)
  val lineValid = validReg(rAddrLine.idx)
  val hit = lineValid && line.tag === rAddrLine.tag

  // FSM
  val sIdle :: sReadCache :: sReq :: sFirstResp :: sFillCache :: Nil = Enum(5)
  val state = RegInit(sIdle)
  val nextState = MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(io.cached.ar.valid, sReadCache, sIdle),
      sReadCache -> Mux(
        hit && inWhiteList,
        Mux(io.cached.r.fire, sIdle, sReadCache),
        sReq
      ),
      sReq -> Mux(io.mem.ar.fire, sFirstResp, sReq),
      sFirstResp -> Mux(
        io.mem.r.fire,
        Mux(io.mem.r.bits.last, sIdle, sFillCache),
        sFirstResp
      ),
      sFillCache -> Mux(io.mem.r.fire && io.mem.r.bits.last, sIdle, sFillCache)
    )
  )
  state := nextState

  // Update cache
  val cacheWPtr = if (lineByte == busByte) {
    0.U
  } else {
    val cacheWPtrIntr = Reg(UInt(log2Ceil(burstTimes).W))
    when(io.mem.ar.valid) {
      cacheWPtrIntr := io.cached.ar.bits
        .addr(log2Ceil(lineByte) - 1, log2Ceil(busByte))
    }.elsewhen(io.mem.r.fire) {
      cacheWPtrIntr := cacheWPtrIntr + 1.U
    }
    cacheWPtrIntr
  }

  when(io.mem.r.fire && inWhiteList) {
    cache(rAddrLine.idx).data(cacheWPtr) := io.mem.r.bits.data
    cache(rAddrLine.idx).tag := rAddrLine.tag
  }

  // Mem bus
  io.mem :<= 0.U.asTypeOf(chiselTypeOf(io.mem))
  io.mem.ar.bits.addr := rAddrReg & ~(busByte.U - 1.U)
  io.mem.ar.bits.len := Mux(inWhiteList, (burstTimes - 1).U, 0.U)
  io.mem.ar.bits.size := "b010".U
  io.mem.ar.bits.burst := Axi4Burst.warp.U
  io.mem.ar.valid := state === sReq
  io.mem.r.ready := true.B
  when(io.mem.r.valid) {
    assert(io.mem.r.bits.resp(1) === 0.U)
  }

  // Cached bus
  val cachedRValidReg = RegInit(false.B)
  val cachedRDataReg = Reg(UInt((busByte * 8).toInt.W))
  when(state === sFirstResp && io.mem.r.fire) {
    cachedRValidReg := true.B
    cachedRDataReg := io.mem.r.bits.data
  }.elsewhen(io.cached.r.ready) {
    cachedRValidReg := false.B
  }

  0.U.asTypeOf(chiselTypeOf(io.cached)) :>= io.cached
  io.cached.ar.ready := state === sIdle
  io.cached.r.valid := (state === sReadCache && hit) || cachedRValidReg
  io.cached.r.bits.data := Mux(
    state === sReadCache,
    cache(rAddrLine.idx)
      .data(if (lineByte == busByte) {
        0.U
      } else { rAddrReg(log2Ceil(lineByte) - 1, log2Ceil(busByte)) }),
    cachedRDataReg
  )
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

  val outBits = out.bits
  val pc = exte.pcReg.pc

  val icache = Module(
    new Icache(
      lineNum = 16,
      lineByte = 4,
      busByte = 4,
      if (cfg.ysyxsoc) {
        Some(BigInt("a0000000", 16) until BigInt("c0000000", 16))
      } else { None }
    )
  )

  out.bits.ifuPayload.trap := 0.U.asTypeOf(
    chiselTypeOf(out.bits.ifuPayload.trap)
  )

  val sIdle :: sWaitResp :: sKeepData :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val canValid = RegNext(RegNext(!reset.asBool))

  val nextState = MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(
        icache.io.cached.ar.fire,
        Mux(
          icache.io.cached.r.valid,
          Mux(out.fire, sIdle, sKeepData),
          sWaitResp
        ),
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
  exte.mem :<>= icache.io.mem
  icache.io.cached :<= 0.U.asTypeOf(new Axi4IO)
  icache.io.cached.ar.bits.size := "b010".U
  assert(
    !icache.io.cached.aw.valid && !icache.io.cached.w.valid && !icache.io.cached.b.valid
  )
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
  outBits.ifuPayload.ifu.inst := Mux(
    state === sKeepData,
    dataReg,
    icache.io.cached.r.bits.data
  )
  outBits.ifuPayload.ifu.pc := pc

  if (cfg.isDebug) {
    debug.get := outBits.ifuPayload.ifu.inst
  }
  if (cfg.perf) {
    PerfWhen(
      "instFetch",
      exte.mem.r.fire,
      out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
    )
    val isMemBusy = RegInit(false.B)
    when(!isMemBusy && exte.mem.ar.valid && !exte.mem.r.valid) {
      isMemBusy := true.B
    }
    when(isMemBusy && exte.mem.r.valid) {
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
    // val icacheState = BoringUtils.tapAndRead(icache.state)
    // val icacheNextState = BoringUtils.tapAndRead(icache.nextState)
    // // val icacheSIdle = BoringUtils.tapAndRead(icache.sIdle)
    // // val icacheSRead = BoringUtils.tapAndRead(icache.sReadCache)
    // // val icacheSMiss = BoringUtils.tapAndRead(icache.sMiss)
    // val icacheSIdle = 0.U
    // val icacheSRead = 1.U
    // val icacheSMiss = 2.U
    // val idleToRead =
    //   icacheState === icacheSIdle && icacheNextState === icacheSRead
    // val readToMiss =
    //   icacheState === icacheSRead && icacheNextState === icacheSMiss
    // PerfWhen(
    //   "icacheTotalAcc",
    //   idleToRead,
    //   out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
    // )
    // PerfWhen(
    //   "icacheMiss",
    //   readToMiss,
    //   out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
    // )
    // PerfWhen(
    //   "icacheMissPenalty",
    //   icacheState === icacheSMiss,
    //   out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
    // )
  }
}
