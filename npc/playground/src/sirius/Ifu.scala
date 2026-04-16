package sirius

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import scala.collection.immutable.NumericRange

class Xorshift32 extends Module {
  val io = IO(new Bundle {
    val en = Input(Bool())
    val out = Output(UInt(32.W))
  })
  val reg = RegInit(1.U(32.W))
  io.out := reg
  val tmp1 = reg ^ (reg << 13)
  val tmp2 = tmp1 ^ (tmp1 >> 17)
  val next = tmp2 ^ (tmp2 << 5)
  when(io.en) {
    reg := next
  }
}

class CacheLine(
  tagWidth:   Int,
  burstTimes: Int,
  busByte:    BigInt)
    extends Bundle {
  val tag = UInt(tagWidth.W)
  val data = Vec(burstTimes.toInt, UInt((busByte * 8).toInt.W))
}

class CacheFile(
  setNum:      BigInt,
  wayNum:      BigInt,
  busByte:     BigInt,
  tagWidth:    Int,
  burstTimes:  Int,
  setIdxWidth: Int,
  wayIdxWidth: Int)
    extends Module {
  val io = IO(new Bundle {
    val valid = Input(Bool())
    val write = Input(Bool())
    val setIdx = Input(UInt(setIdxWidth.W))
    val wayIdx = Input(UInt(wayIdxWidth.W))
    val wData = Input(
      new CacheLine(
        tagWidth = tagWidth,
        burstTimes = burstTimes,
        busByte = busByte
      )
    )
    val rData = Output(
      Vec(
        wayNum.toInt,
        new CacheLine(
          tagWidth = tagWidth,
          burstTimes = burstTimes,
          busByte = busByte
        )
      )
    )
  })

  val cache = Mem(
    setNum.toInt,
    Vec(
      wayNum.toInt,
      new CacheLine(
        tagWidth = tagWidth,
        burstTimes = burstTimes,
        busByte = busByte
      )
    )
  )

  val wData = VecInit.fill(wayNum.toInt)(io.wData)
  io.rData := DontCare
  when(io.valid) {
    io.rData := cache.read(io.setIdx)
    when(io.write) {
      cache.write(io.setIdx, wData, UIntToOH(io.wayIdx).asBools)
    }
  }
}

class Icache(
  setNum:    BigInt,
  wayNum:    BigInt,
  wayByte:   BigInt,
  busByte:   BigInt,
  whiteList: Option[NumericRange[BigInt]] = None)
    extends Module {
  require(setNum > 0 && setNum.bitCount == 1)
  require(wayNum > 0 && wayNum.bitCount == 1)
  require(wayByte >= busByte && wayByte.bitCount == 1)
  require(busByte > 0 && busByte.bitCount == 1)
  val wayIdxWidth = log2Ceil(wayNum).toInt
  val burstTimes = (wayByte / busByte).toInt
  val offWidth = 2
  val dataIdxWidth = (log2Ceil(wayByte) - offWidth).toInt
  val setIdxWidth = log2Ceil(setNum).toInt
  val tagWidth = (busByte * 8 - offWidth - dataIdxWidth - setIdxWidth).toInt

  val io = IO(new Bundle {
    val cached = Flipped(new Axi4IO)
    val mem = new Axi4IO
    val flush = Input(Bool())
  })

  assert(!io.cached.aw.valid && !io.cached.w.valid)

  // Addr
  class AddrLine extends Bundle {
    val tag = UInt(tagWidth.W)
    val setIdx = UInt(setIdxWidth.W)
    val dataIdx = UInt(dataIdxWidth.W)
    val off = UInt(offWidth.W)
  }
  val rAddrReg = RegEnable(io.cached.ar.bits.addr, io.cached.ar.fire)
  val rAddrLine = rAddrReg.asTypeOf(new AddrLine)
  val inWhiteList = if (whiteList.isDefined) {
    rAddrReg >= whiteList.get.start.U && rAddrReg < whiteList.get.end.U
  } else { true.B }

  // Random
  val xorshift32 = Module(new Xorshift32)
  val rand = xorshift32.io.out

  // Cache
  val cache = Module(
    new CacheFile(
      setNum = setNum,
      wayNum = wayNum,
      busByte = busByte,
      tagWidth = tagWidth,
      burstTimes = burstTimes,
      setIdxWidth = setIdxWidth,
      wayIdxWidth = wayIdxWidth
    )
  )
  cache.io.wData := DontCare

  val setIdx = if (setIdxWidth != 0) { rAddrLine.setIdx }
  else { 0.U }
  cache.io.setIdx := setIdx

  val valids = RegInit(0.U.asTypeOf(Vec(setNum.toInt, Vec(wayNum.toInt, Bool()))))
  when(io.flush) {
    valids := 0.U.asTypeOf(chiselTypeOf(valids))
  }
  val invalidWayIdx = PriorityEncoder(~valids(setIdx).asUInt)
  val isAllValid = valids(setIdx).asUInt.andR
  val allValidWayIdx = if (wayIdxWidth != 0) { rand(31, 31 - wayIdxWidth + 1) }
  else { 0.U }
  val wayIdx = Mux(isAllValid, allValidWayIdx, invalidWayIdx)
  cache.io.wayIdx := wayIdx

  val (isHit, hitData) = valids(setIdx)
    .zip(cache.io.rData)
    .map { case (valid, line) =>
      val isHit = valid && (line.tag === rAddrLine.tag)
      val data = line.data.asUInt & Fill(line.data.getWidth, isHit)
      (isHit, data.asTypeOf(chiselTypeOf(cache.io.rData.head.data)))
    }
    .reduce { (a, b) =>
      (a._1 || b._1, (a._2.asUInt | b._2.asUInt).asTypeOf(chiselTypeOf(cache.io.rData.head.data)))
    }

  // val hits = VecInit(valids(setIdx).zip(cache.io.rData).map { case (valid, line) =>
  //   valid && (line.tag === rAddrLine.tag)
  // })
  // val isHit = hits.asUInt =/= 0.U
  // val hitData = cache.io.rData(OHToUInt(hits)).data

  // FSM
  val sIdle :: sReadCache :: sReq :: sFirstResp :: sFillCache :: Nil = Enum(5)
  val state = RegInit(sIdle)
  val nextState = WireDefault(state)
  state := nextState
  switch(state) {
    is(sIdle) {
      when(io.cached.ar.valid) {
        nextState := sReadCache
      }
    }
    is(sReadCache) {
      when(isHit && inWhiteList) {
        when(io.cached.r.fire) {
          nextState := sIdle
        }
      }.otherwise {
        nextState := sReq
      }
    }
    is(sReq) {
      when(io.mem.ar.fire) {
        nextState := sFirstResp
      }
    }
    is(sFirstResp) {
      when(io.mem.r.fire) {
        nextState := Mux(io.mem.r.bits.last, sIdle, sFillCache)
      }
    }
    is(sFillCache) {
      when(io.mem.r.fire && io.mem.r.bits.last) {
        nextState := sIdle
      }
    }
  }
  // val nextState = MuxLookup(state, sIdle)(
  //   Seq(
  //     sIdle -> Mux(io.cached.ar.valid, sReadCache, sIdle),
  //     sReadCache -> Mux(
  //       isHit && inWhiteList,
  //       Mux(io.cached.r.fire, sIdle, sReadCache),
  //       sReq
  //     ),
  //     sReq -> Mux(io.mem.ar.fire, sFirstResp, sReq),
  //     sFirstResp -> Mux(
  //       io.mem.r.fire,
  //       Mux(io.mem.r.bits.last, sIdle, sFillCache),
  //       sFirstResp
  //     ),
  //     sFillCache -> Mux(io.mem.r.fire && io.mem.r.bits.last, sIdle, sFillCache)
  //   )
  // )
  // state := nextState

  // Update cache
  xorshift32.io.en := state =/= sFirstResp && state =/= sFillCache
  cache.io.write := io.mem.r.fire && inWhiteList
  cache.io.valid := cache.io.write || state === sReadCache

  val dataIdx = if (wayByte == busByte) {
    0.U
  } else {
    val dataIdxReg = Reg(UInt(log2Ceil(burstTimes).W))
    when(io.mem.ar.valid) {
      dataIdxReg := rAddrLine.dataIdx
    }.elsewhen(io.mem.r.fire) {
      dataIdxReg := dataIdxReg + 1.U
    }
    dataIdxReg
  }

  when(io.mem.r.fire && inWhiteList) {
    val line = WireDefault(cache.io.rData(wayIdx))
    line.data(dataIdx) := io.mem.r.bits.data
    line.tag := rAddrLine.tag
    cache.io.wData := line
    when(io.mem.r.bits.last) {
      valids(setIdx)(wayIdx) := true.B
    }
  }

  // Mem bus
  io.mem :<= 0.U.asTypeOf(chiselTypeOf(io.mem))
  io.mem.ar.bits.addr := rAddrReg & ~((busByte - 1).U(rAddrReg.getWidth.W))
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
  io.cached.r.valid := (state === sReadCache && isHit) || cachedRValidReg
  io.cached.r.bits.data := Mux(
    state === sReadCache,
    hitData(rAddrLine.dataIdx),
    cachedRDataReg
  )
}

class Ifu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val exte = IO(new Bundle {
    val pcReg = new IfuToPcRegIO
    val mem = new Axi4IO
    val globalCtrl = Flipped(new GlobalCtrl)
    val flush = Input(Bool())
    val debugEbreak = Option.when(cfg.isDebug)(Input(Bool()))
  })
  val out = IO(Decoupled(new IfuToIduIO))
  // val debug = Option.when(cfg.isDebug)(IO(Output(UInt(cfg.xlen.W))))

  val outBits = out.bits
  val pc = exte.pcReg.pc

  val icache = Module(
    new Icache(
      setNum = 2,
      wayNum = 8,
      wayByte = 8,
      busByte = 4,
      if (cfg.ysyxsoc) {
        Some(BigInt("a0000000", 16) until BigInt("c0000000", 16))
      } else { None }
    )
  )
  icache.io.flush := exte.globalCtrl.globalCtrl.isFlushIcache

  out.bits.ifuPayload.trap := 0.U.asTypeOf(
    chiselTypeOf(out.bits.ifuPayload.trap)
  )

  val sReq :: sResp :: Nil = Enum(2)
  val state = RegInit(sReq)
  val canValid = RegNext(RegNext(!reset.asBool))
  val flushReg = RegInit(false.B)
  when(exte.flush) {
    flushReg := true.B
  }
  when(icache.io.cached.r.fire) {
    flushReg := false.B
  }

  val nextState = MuxLookup(state, sReq)(
    Seq(
      sReq -> Mux(
        icache.io.cached.ar.fire,
        sResp,
        sReq
      ),
      sResp -> Mux(
        icache.io.cached.r.fire,
        sReq,
        sResp
      )
    )
  )
  state := nextState

  exte.mem :<>= icache.io.mem
  icache.io.cached :<= 0.U.asTypeOf(new Axi4IO)
  icache.io.cached.ar.bits.size := "b010".U
  assert(
    !icache.io.cached.aw.valid && !icache.io.cached.w.valid && !icache.io.cached.b.valid
  )
  icache.io.cached.ar.valid := state === sReq && canValid
  icache.io.cached.ar.bits.addr := pc
  icache.io.cached.r.ready := out.ready || flushReg

  out.valid := icache.io.cached.r.valid && !flushReg
  outBits.ifuPayload.ifu.inst := icache.io.cached.r.bits.data
  outBits.ifuPayload.ifu.pc := pc

  // if (cfg.isDebug) {
  //   debug.get := outBits.ifuPayload.ifu.inst
  // }
  if (cfg.perf) {
    PerfWhen(
      "instFetch",
      icache.io.cached.r.fire,
      exte.debugEbreak
    )
    // val isMemBusy = RegInit(false.B)
    // when(!isMemBusy && exte.mem.ar.valid && !exte.mem.r.valid) {
    //   isMemBusy := true.B
    // }
    // when(isMemBusy && exte.mem.r.valid) {
    //   isMemBusy := false.B
    // }
    PerfWhen(
      "waitReadCyc",
      !out.valid && out.ready,
      exte.debugEbreak
    )
    PerfWhen(
      "keepDataCyc",
      out.valid && !out.ready,
      exte.debugEbreak
    )
    val icacheState = BoringUtils.tapAndRead(icache.state)
    val icacheNextState = BoringUtils.tapAndRead(icache.nextState)
    val icacheInWhiteList = BoringUtils.tapAndRead(icache.inWhiteList)
    val icacheSIdle = 0.U
    val icacheSReadCache = 1.U
    val icacheSReq = 2.U
    val icacheSFirstResp = 3.U
    val icacheSFillCache = 4.U
    PerfWhen(
      "icacheTotalAcc",
      icacheState === icacheSIdle && icacheNextState === icacheSReadCache,
      exte.debugEbreak
    )
    PerfWhen(
      "icacheMiss",
      icacheState === icacheSReadCache && icacheNextState === icacheSReq && icacheInWhiteList,
      exte.debugEbreak
    )
    PerfWhen(
      "icacheHit",
      icacheState === icacheSReadCache && icacheNextState === icacheSIdle,
      exte.debugEbreak
    )
    PerfWhen(
      "icacheBlackList",
      icacheState === icacheSReadCache && icacheNextState === icacheSReq && !icacheInWhiteList,
      exte.debugEbreak
    )
    PerfWhen(
      "icacheMissPenalty",
      icacheInWhiteList && (icacheState =/= icacheSIdle && icacheState =/= icacheSReadCache),
      exte.debugEbreak
    )
  }
}
