package sirius

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import scala.collection.immutable.NumericRange

class Icache(
  lineNum:   BigInt,
  lineByte:  BigInt,
  addrByte:  BigInt,
  whiteList: Option[NumericRange[BigInt]] = None)
    extends Module {
  require(lineNum > 0 && lineNum.bitCount == 1)
  require(lineByte > 0 && lineByte.bitCount == 1)
  require(lineByte == 4)

  val io = IO(new Bundle {
    val cached = Flipped(new Axi4IO)
    val mem = new Axi4IO
  })

  assert(!io.cached.aw.valid && !io.cached.w.valid)

  class AddrLine extends Bundle {
    val tag = UInt(
      (addrByte * 8 - log2Ceil(lineByte) - log2Ceil(lineNum)).toInt.W
    )
    val idx = UInt(log2Ceil(lineNum).W)
    val off = UInt(log2Ceil(lineByte).W)
  }
  val rAddrReg = RegEnable(io.cached.ar.bits.addr, io.cached.ar.valid)
  val rAddr = Mux(io.cached.ar.valid, io.cached.ar.bits.addr, rAddrReg)
  rAddrReg := rAddr
  val rAddrLine = rAddr.asTypeOf(new AddrLine)
  val inWhireList = if (whiteList.isDefined) {
    rAddr >= whiteList.get.start.U && rAddr < whiteList.get.end.U
  } else { true.B }

  class Line extends Bundle {
    val tag = UInt(
      (addrByte * 8 - log2Ceil(lineByte) - log2Ceil(lineNum)).toInt.W
    )
    val data = UInt((lineByte * 8).toInt.W)
  }
  val cache = Mem(lineNum.toInt, new Line)
  val validReg = RegInit(0.U.asTypeOf(Vec(lineNum.toInt, Bool())))
  val line = cache.read(rAddrLine.idx)
  val lineValid = validReg(rAddrLine.idx)
  val hit = lineValid && line.tag === rAddrLine.tag

  val sIdle :: sReadCache :: sMiss :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val nextState = MuxLookup(state, sIdle)(
    Seq(
      sIdle -> Mux(io.cached.ar.valid && inWhireList, sReadCache, sIdle),
      sReadCache -> Mux(hit, Mux(io.cached.r.fire, sIdle, sReadCache), sMiss),
      sMiss -> Mux(io.mem.r.fire, sIdle, sMiss)
    )
  )
  state := nextState

  0.U.asTypeOf(chiselTypeOf(io.cached)) :>= io.cached
  io.mem :<= 0.U.asTypeOf(chiselTypeOf(io.mem))
  io.cached.ar.ready := state === sReadCache && hit
  io.cached.r.valid := state === sReadCache && hit
  io.cached.r.bits.data := line.data
  when(state === sMiss || (state === sIdle && !inWhireList)) {
    io.mem :<>= io.cached
  }
  when(state === sMiss && io.mem.r.fire) {
    lineValid := true.B
    cache.write(
      rAddrLine.idx,
      (rAddrLine.tag ## io.mem.r.bits.data).asTypeOf(new Line)
    )
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

  val outBits = out.bits
  val pc = exte.pcReg.pc

  val icache = Module(
    new Icache(
      lineNum = 16,
      lineByte = 4,
      addrByte = 4,
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
    val icacheState = BoringUtils.tapAndRead(icache.state)
    val icacheNextState = BoringUtils.tapAndRead(icache.nextState)
    val icacheSIdle = BoringUtils.tapAndRead(icache.sIdle)
    val icacheSRead = BoringUtils.tapAndRead(icache.sReadCache)
    val icacheSMiss = BoringUtils.tapAndRead(icache.sMiss)
    val idleToRead =
      icacheState === icacheSIdle && icacheNextState === icacheSRead
    val readToMiss = 
      icacheState === icacheSRead && icacheNextState === icacheSMiss
    PerfWhen(
      "icacheTotalAcc",
      idleToRead,
      out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
    )
    PerfWhen(
      "icacheMiss",
      readToMiss,
      out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
    )
    PerfWhen(
      "icacheMissPenalty",
      icacheState === icacheSMiss,
      out.valid && (out.bits.ifuPayload.ifu.inst === "h00100073".U)
    )
  }
}
