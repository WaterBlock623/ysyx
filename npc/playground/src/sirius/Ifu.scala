package sirius

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import scala.collection.immutable.NumericRange
import chisel3.util.random.MaxPeriodGaloisLFSR

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

class IcacheIO(
  implicit private val cfg: CoreConfig)
    extends Bundle {
  val abort = Bool()
  val fencei = Bool()
  val ar = Decoupled(new Bundle {
    val addr = UInt(cfg.xlen.W)
  })
  val r = Flipped(Decoupled(new Bundle {
    val data = UInt(cfg.xlen.W)
  }))
}

class Icache(
  setNum:    BigInt,
  wayNum:    BigInt,
  wayByte:   BigInt,
  busByte:   BigInt,
  whiteList: Option[NumericRange[BigInt]] = None
)(
  implicit private val cfg: CoreConfig)
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
    // val cached = Flipped(new Axi4IO)
    val cached = Flipped(new IcacheIO)
    val mem = new Axi4IO
  })

  val sReadCache :: sReq :: sFirstResp :: sFillCache :: sWait :: Nil = Enum(5)
  val state = RegInit(sReadCache)

  // Addr
  class AddrLine extends Bundle {
    val tag = UInt(tagWidth.W)
    val setIdx = UInt(setIdxWidth.W)
    val dataIdx = UInt(dataIdxWidth.W)
    val off = UInt(offWidth.W)
  }
  val rAddrReg = RegEnable(io.cached.ar.bits.addr, state === sReadCache)
  val rAddr = Mux(state === sReadCache, io.cached.ar.bits.addr, rAddrReg)
  // val rAddr = io.cached.ar.bits.addr
  val rAddrLine = rAddr.asTypeOf(new AddrLine)
  val inWhiteList = if (whiteList.isDefined) {
    rAddr >= whiteList.get.start.U && rAddr < whiteList.get.end.U
  } else { true.B }

  // Random
  val xorshift32 = Module(new Xorshift32)
  val rand = xorshift32.io.out
  // val lfsr = Module(new MaxPeriodGaloisLFSR(64))
  // lfsr.io.seed := DontCare
  // val rand = lfsr.io.out.asUInt

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
  when(io.cached.fencei) {
    valids := 0.U.asTypeOf(chiselTypeOf(valids))
  }
  val invalidWayIdx = PriorityEncoder(~valids(setIdx).asUInt)
  val isAllValid = valids(setIdx).asUInt.andR
  val allValidWayIdx = if (wayIdxWidth != 0) { rand.head(wayIdxWidth) }
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
  val abortReg = RegInit(false.B)
  // val sIdle :: sReadCache :: sReq :: sFirstResp :: sFillCache :: Nil = Enum(5)
  val nextState = WireDefault(state)
  state := nextState

  val rFiredReg = RegInit(false.B)
  when (io.cached.r.fire || io.cached.abort) {
    rFiredReg := true.B
  }
  when (nextState === sReadCache) {
    rFiredReg := false.B
  }
  val rFired = io.cached.r.fire || io.cached.abort || rFiredReg

  switch(state) {
    is(sReadCache) {
      when(io.cached.ar.valid && !(isHit && inWhiteList)) {
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
        nextState := Mux(io.mem.r.bits.last, Mux(rFired, sReadCache, sWait), sFillCache)
      }
    }
    is(sFillCache) {
      when(io.mem.r.fire && io.mem.r.bits.last) {
        nextState := Mux(rFired, sReadCache, sWait)
      }
    }
    is(sWait) {
      when(io.cached.r.fire) {
        nextState := sReadCache
      }
    }
  }

  // Update cache
  xorshift32.io.en := state =/= sFirstResp && state =/= sFillCache
  // lfsr.io.increment := state =/= sFirstResp && state =/= sFillCache
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
  io.mem.ar.bits.addr := rAddr & ~((busByte - 1).U(rAddr.getWidth.W))
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
  when(abortReg || io.cached.abort) {
    cachedRValidReg := false.B
  }

  when(io.cached.abort) {
    abortReg := true.B
  }
  when(nextState === sReadCache) {
    abortReg := false.B
  }

  0.U.asTypeOf(chiselTypeOf(io.cached)) :>= io.cached
  // io.cached.ar.ready := state === sIdle && (!cachedRValidReg || io.cached.r.fire)
  io.cached.ar.ready := !abortReg && Mux(state =/= sReadCache, nextState === sReadCache, io.cached.r.fire)
  io.cached.r.valid := !abortReg && ((io.cached.ar.valid && state === sReadCache && isHit) || cachedRValidReg)
  io.cached.r.bits.data := Mux(
    cachedRValidReg,
    cachedRDataReg,
    hitData(rAddrLine.dataIdx)
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
    val jumpTarget = Input(UInt(cfg.xlen.W))
    val debugEbreak = Option.when(cfg.isDebug)(Input(Bool()))
  })
  val out = IO(Decoupled(new IfuToIduIO))
  val outBits = out.bits

  // icache

  if (!cfg.formal) {
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
    exte.mem :<>= icache.io.mem
    val cached = icache.io.cached
    cached.fencei := exte.globalCtrl.globalCtrl.isFlushIcache
    cached.abort := exte.flush
    cached.ar.valid := true.B
    val ifetchAddr = RegInit(cfg.pcInit.U(cfg.xlen.W))
    when(exte.flush) {
      ifetchAddr := exte.jumpTarget
    }.elsewhen(cached.ar.fire) {
      ifetchAddr := ifetchAddr + 4.U
    }
    cached.ar.bits.addr := ifetchAddr
    cached.r.ready := out.ready

    exte.pcReg.update := cached.r.fire
    out.valid := cached.r.valid
    outBits.ifuPayload.ifu.inst := cached.r.bits.data

    if (cfg.perf) {
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
      PerfWhen(
        "instFetch",
        icache.io.cached.r.fire,
        exte.debugEbreak
      )
    }
  } else {
    val ifetchAddr = RegInit(cfg.pcInit.U(cfg.xlen.W))
    when(exte.flush) {
      ifetchAddr := exte.jumpTarget
    }.elsewhen(exte.mem.ar.fire) {
      ifetchAddr := ifetchAddr + 4.U
    }

    exte.mem :<= 0.U.asTypeOf(chiselTypeOf(exte.mem))
    exte.mem.ar.valid := true.B
    exte.mem.ar.bits.addr := ifetchAddr
    exte.mem.ar.bits.size := "b010".U
    exte.mem.ar.bits.burst := Axi4Burst.incr.U
    exte.mem.r.ready := out.ready

    exte.pcReg.update := exte.mem.r.fire
    out.valid := exte.mem.r.valid
    outBits.ifuPayload.ifu.inst := exte.mem.r.bits.data
  }

  // pc
  val staticNextPc = exte.pcReg.pc + 4.U
  // exte.pcReg.update := cached.r.fire
  exte.pcReg.staticNextPc := staticNextPc

  // out
  // out.valid := cached.r.valid
  out.bits.ifuPayload.trap.isTrap := false.B
  out.bits.ifuPayload.trap.cause := DontCare
  // outBits.ifuPayload.ifu.inst := cached.r.bits.data
  outBits.ifuPayload.ifu.pc := exte.pcReg.pc
  // outBits.ifuPayload.ifu.staticNextPc := staticNextPc

  // debug
  if (cfg.formal) {
    import rvspeccore.checker._
    implicit val XLEN: Int = cfg.xlen
    when(out.valid) {
      val inst = out.bits.ifuPayload.ifu.inst
      assume(
        RVI(inst) ||
          RVZifencei(inst) ||
          {
            val allowCsr = Set(
              CsrAddr.mcycle,
              CsrAddr.mcycleh,
              CsrAddr.mepc,
              // CsrAddr.mstatus,
              CsrAddr.mtvec
            )
            RVZicsr(inst) && allowCsr.map(_.U === inst(31, 20)).reduce(_ || _)
          }
      )
    }
  }

  if (cfg.perf) {
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
  }
}
