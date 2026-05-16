package sirius

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

class Ifu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val exte = IO(new Bundle {
    // val pcReg = new IfuToPcRegIO
    val bpu = new IfuToBpuIO
    val mem = new Axi4IO
    val fencei = Input(Bool())
    val flush = Input(Bool())
    val jumpTarget = Input(UInt(cfg.xlen.W))
    val debugEbreak = Option.when(cfg.isDebug)(Input(Bool()))
  })
  val out = IO(Decoupled(new IfuToIduIO))
  val outBits = out.bits

  outBits.ifuPayload.trap.isTrap := false.B
  outBits.ifuPayload.trap.cause := DontCare

  val flush = exte.flush || (out.fire && exte.bpu.taken)
  val flushTarget = Mux(exte.flush, exte.jumpTarget, exte.bpu.target)

  if (!cfg.formal) {
    // icache
    val icache = Module(
      new Icache(
        setNum = 1,
        wayNum = 8,
        wayByte = 16,
        busByte = 4,
        if (cfg.ysyxsoc) {
          Some(BigInt("a0000000", 16) until BigInt("c0000000", 16))
        } else { None }
      )
    )
    exte.mem :<>= icache.io.mem
    val cached = icache.io.cached
    cached.fencei := exte.fencei
    cached.abort := flush
    cached.ar.valid := true.B
    val ifetchAddr = RegInit(cfg.pcInit.U(cfg.xlen.W))
    when(flush) {
      ifetchAddr := flushTarget
    }.elsewhen(cached.ar.fire) {
      ifetchAddr := ifetchAddr + 4.U
    }
    cached.ar.bits.addr := ifetchAddr

    val iqueue = Module(new Iqueue(entries32 = 2))
    iqueue.io.flush := flush
    iqueue.io.targetUnalign := flushTarget(1)
    iqueue.io.enq :<>= cached.r.map(_.data)

    def pipelineConnect[T <: Data](
      prevOut: DecoupledIO[T],
      thisIn:  DecoupledIO[T],
      stall:   Bool = false.B,
      flush:   Bool = false.B
    ) = {
      val thisInReady = thisIn.ready || !thisIn.valid
      val valid = RegInit(false.B)
      when(thisInReady) {
        valid := prevOut.valid && !stall
      }
      when(flush) {
        valid := false.B
      }

      prevOut.ready := thisInReady && !stall
      thisIn.valid := valid
      thisIn.bits := RegEnable(prevOut.bits, prevOut.fire)
    }

    // val iqueueDeq = Wire(Flipped(chiselTypeOf(iqueue.io.deq)))
    // pipelineConnect(iqueue.io.deq, iqueueDeq, flush = flush)
    val iqueueDeq = iqueue.io.deq

    out.valid := iqueueDeq.valid
    iqueueDeq.ready := out.ready
    val pc = RegInit(cfg.pcInit.U(cfg.xlen.W))
    when(flush) {
      pc := flushTarget
    }.elsewhen(out.fire) {
      pc := pc + Mux(iqueueDeq.bits.isC, 2.U, 4.U)
    }
    exte.bpu.pc := pc

    outBits.ifuPayload.ifu.pc := pc
    outBits.ifuPayload.ifu.predTaken := exte.bpu.taken
    outBits.ifuPayload.ifu.predTarget := exte.bpu.target
    outBits.ifuPayload.ifu.inst := iqueueDeq.bits.inst
    outBits.ifuPayload.ifu.isC := iqueueDeq.bits.isC

    if (cfg.perf) {
      val icacheState = BoringUtils.tapAndRead(icache.state)
      val icacheNextState = BoringUtils.tapAndRead(icache.nextState)
      val icacheInWhiteList = BoringUtils.tapAndRead(icache.inWhiteList)
      val icacheSReadCache = 0.U
      val icacheSReq = 1.U
      val icacheSFirstResp = 2.U
      val icacheSFillCache = 3.U
      val icacheSWait = 4.U
      PerfWhen(
        "icacheTotalAcc",
        icache.io.cached.ar.fire,
        exte.debugEbreak
      )
      PerfWhen(
        "icacheMiss",
        icacheState === icacheSReadCache && icacheNextState === icacheSReq && icacheInWhiteList,
        exte.debugEbreak
      )
      PerfWhen(
        "icacheHit",
        icache.io.cached.ar.valid && icacheState === icacheSReadCache && (icacheNextState === icacheSReadCache || icacheNextState === icacheSWait),
        exte.debugEbreak
      )
      PerfWhen(
        "icacheBlackList",
        icacheState === icacheSReadCache && icacheNextState === icacheSReq && !icacheInWhiteList,
        exte.debugEbreak
      )
      PerfWhen(
        "icacheMissPenalty",
        icacheInWhiteList && (icacheState =/= icacheSReadCache) && (icacheState =/= icacheSWait),
        exte.debugEbreak
      )
      PerfWhen(
        "instFetch",
        icache.io.cached.r.fire,
        exte.debugEbreak
      )
    }
  } else {
    // val ifetchAddr = RegInit(cfg.pcInit.U(cfg.xlen.W))
    // when(exte.flush) {
    //   ifetchAddr := exte.jumpTarget
    // }.elsewhen(exte.mem.ar.fire) {
    //   ifetchAddr := Mux(exte.bpu.taken, exte.bpu.target, ifetchAddr + 4.U)
    // }

    exte.mem :<= 0.U.asTypeOf(chiselTypeOf(exte.mem))
    exte.mem.ar.valid := true.B
    // exte.mem.ar.bits.addr := ifetchAddr
    exte.mem.ar.bits.addr := DontCare
    exte.mem.ar.bits.size := "b010".U
    exte.mem.ar.bits.burst := Axi4Burst.incr.U
    exte.mem.r.ready := out.ready

    out.valid := exte.mem.r.valid
    outBits.ifuPayload.ifu.inst := exte.mem.r.bits.data
  
    val pc = RegInit(cfg.pcInit.U(cfg.xlen.W))
    when(flush) {
      pc := flushTarget
    }.elsewhen(out.fire) {
      pc := pc + Mux(outBits.ifuPayload.ifu.isC, 2.U, 4.U)
    }

    exte.bpu.pc := pc

    outBits.ifuPayload.ifu.pc := pc
    outBits.ifuPayload.ifu.predTaken := exte.bpu.taken
    outBits.ifuPayload.ifu.predTarget := exte.bpu.target
    outBits.ifuPayload.ifu.isC := outBits.ifuPayload.ifu.inst(1, 0) =/= "b11".U
  }

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
          } ||
          RVC(inst)
      )
    }
  }

  if (cfg.perf) {
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
