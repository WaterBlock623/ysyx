package sirius

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

class BasicCore(
  implicit private val cfg:  CoreConfig,
  implicit private val ucfg: UnitConfig)
    extends Module {
  val io = IO(new Bundle {
    val axiIfu = new Axi4IO
    val axiLsu = new Axi4IO
  })

  val pcReg = Module(new PcReg)
  val registerFile = Module(new RegisterFile)
  val csr = Module(new Csr)
  val ifu = Module(new Ifu)
  val idu = Module(new Idu)
  val exu = Module(new Exu)
  val lsu = Module(new Lsu)
  val wbu = Module(new Wbu)

  val ifuOut = ifu.out
  val iduOut = idu.out
  val exuOut = exu.out
  val lsuOut = lsu.out

  val globalCtrl = idu.exte.globalCtrl

  io.axiIfu :<>= ifu.exte.mem
  io.axiLsu :<>= lsu.exte.mem
  pcReg.ifuIn :<>= ifu.exte.pcReg
  registerFile.iduIn :<>= idu.exte.regFile
  registerFile.wbuIn :<>= wbu.exte.regFlie
  csr.exuIn :<>= exu.exte.csr
  csr.wbuIn :<>= wbu.exte.csr
  ifu.exte.globalCtrl := globalCtrl

  if (cfg.pipeline) {
    def pipelineConnect[T <: Data](
      prevOut: DecoupledIO[T],
      thisIn:  DecoupledIO[T],
      stall:   Bool = false.B,
      flush:   Bool = false.B
    ) = {
      val ready = thisIn.ready || !thisIn.valid
      prevOut.ready := ready && !stall
      thisIn.bits := RegEnable(prevOut.bits, prevOut.fire)
      // thisIn.valid := RegEnable(prevOut.valid && !stall, false.B, ready)
      val valid = Reg(Bool())
      thisIn.valid := valid
      when(ready) {
        valid := prevOut.valid && !stall
      }
      when(flush) {
        valid := false.B
      }
    }

    val stallIdu = Wire(Bool())
    val stallExu = Wire(Bool())
    val flushIfu = Wire(Bool())
    val flushIdu = Wire(Bool())
    val flushExu = Wire(Bool())
    pipelineConnect(ifuOut, idu.in, flush = flushIfu)
    pipelineConnect(iduOut, exu.in, stall = stallIdu, flush = flushIdu)
    pipelineConnect(exuOut, lsu.in, stall = stallExu, flush = flushExu)
    pipelineConnect(lsuOut, wbu.in)

    val readRs1 = globalCtrl.globalCtrl.readRs1
    val rs1 = idu.exte.regFile.rAddr(0)
    val readRs2 = globalCtrl.globalCtrl.readRs2
    val rs2 = idu.exte.regFile.rAddr(1)
    case class StageRd(valid: Bool, isWriteBack: Bool, rd: UInt)
    val stageRds = Seq(
      StageRd(
        exu.in.valid,
        exu.in.bits.ctrl.wbuCtrl.isWriteBackReg,
        exu.in.bits.iduPayload.idu.wAddr
      ),
      StageRd(
        lsu.in.valid,
        lsu.in.bits.ctrl.wbuCtrl.isWriteBackReg,
        lsu.in.bits.exuPayload.idu.wAddr
      ),
      StageRd(
        wbu.in.valid,
        wbu.in.bits.ctrl.wbuCtrl.isWriteBackReg,
        wbu.in.bits.lsuPayload.idu.wAddr
      )
    )
    def conflict(stageRd: StageRd, rs: UInt): Bool = {
      stageRd.valid && stageRd.isWriteBack && stageRd.rd === rs
    }
    val rs1Conflict = readRs1 && rs1 =/= 0.U && stageRds.map(s => conflict(s, rs1)).reduce(_ || _)
    val rs2Conflict = readRs2 && rs2 =/= 0.U && stageRds.map(s => conflict(s, rs2)).reduce(_ || _)
    val isRaw = rs1Conflict || rs2Conflict
    stallIdu := isRaw

    case class StageJump(valid: Bool, isJump: Bool, isBranch: Bool)
    val stageJumps = Seq(
      StageJump(
        lsu.in.valid,
        lsu.in.bits.ctrl.wbuCtrl.isJump,
        lsu.in.bits.ctrl.wbuCtrl.isBranch
      ),
      StageJump(
        wbu.in.valid,
        wbu.in.bits.ctrl.wbuCtrl.isJump,
        wbu.in.bits.ctrl.wbuCtrl.isBranch
      )
    )
    val willJump = stageJumps.map(s => s.valid && (s.isJump || s.isBranch)).reduce(_ || _)
    pcReg.wbuIn.target := wbu.exte.pcReg.target
    pcReg.wbuIn.isJump := wbu.exte.pcReg.wEn && wbu.exte.pcReg.isJump
    pcReg.wbuIn.wEn := pcReg.wbuIn.isJump || ifuOut.fire
    stallExu := willJump
    flushIfu := pcReg.wbuIn.isJump
    flushIdu := pcReg.wbuIn.isJump
    flushExu := pcReg.wbuIn.isJump
    ifu.exte.flush := pcReg.wbuIn.isJump
  } else {
    pcReg.wbuIn :<>= wbu.exte.pcReg
    ifu.exte.flush := false.B
    idu.in :<>= ifuOut
    exu.in :<>= iduOut
    lsu.in :<>= exuOut
    wbu.in :<>= lsuOut
  }
}

class Top(
  implicit private val cfg:  CoreConfig,
  implicit private val ucfg: UnitConfig)
    extends Module {

  val basicCore = Module(new BasicCore)
  val xbar = Module(
    new Xbar(
      2,
      2,
      Seq(
        addr => addr < "h02000000".U || addr >= "h02010000".U,
        addr => addr >= "h02000000".U && addr < "h02010000".U
      )
    )
  )
  val clintDevice = Module(new ClintDevice)

  xbar.io.in(0) :<>= basicCore.io.axiLsu
  xbar.io.in(1) :<>= basicCore.io.axiIfu
  clintDevice.in :<>= xbar.io.out(1)

  if (cfg.ysyxsoc) {
    val io = IO(new Bundle {
      val interrupt = Input(Bool())
      val master = new Axi4FlatIO
      val slave = Flipped(new Axi4FlatIO)
    })
    0.U.asTypeOf(chiselTypeOf(io.slave)) :>= io.slave
    io.master :<>= xbar.io.out(0).viewAs[Axi4FlatIO]
  } else if (cfg.isDebug) {
    val memDpiC = Module(new MemDpiC)
    val axi4BurstSpliter = Module(new Axi4BurstSpliter)
    axi4BurstSpliter.io.in :<>= xbar.io.out(0)
    memDpiC.axi :<>= axi4BurstSpliter.io.out.viewAs[Axi4FlatIO]
    memDpiC.clock := clock
    memDpiC.reset := reset
  }

  if (cfg.isDebug) {
    import chisel3.util.experimental.BoringUtils._
    val ebreaks = Seq(
      basicCore.ifu.exte.debugEbreak,
      basicCore.idu.exte.debugEbreak,
      basicCore.exu.exte.debugEbreak,
      basicCore.lsu.exte.debugEbreak,
      basicCore.wbu.exte.debugEbreak
    )
    val wbuIn = tapAndRead(basicCore.wbu.in)
    val ebreakSignal = wbuIn.bits.ctrl.debugCtrl.get.isEbreak
    ebreaks.foreach { e => drive(e.get) := ebreakSignal }

    val debugInfoDpiC = Module(new DebugInfoDpiC)
    val getGprDpiC = Module(new GetGprDpiC)
    debugInfoDpiC.isEbreak := ebreakSignal
    debugInfoDpiC.pc := wbuIn.bits.lsuPayload.ifu.pc
    debugInfoDpiC.pcRaw := tapAndRead(basicCore.pcReg.debug.get.pc)
    // debugInfoDpiC.dnpc := pcReg.debug.get.dnpc
    debugInfoDpiC.inst := wbuIn.bits.lsuPayload.ifu.inst
    debugInfoDpiC.wbuValid := tapAndRead(basicCore.wbu.debug.get.valid)
    debugInfoDpiC.isJump := tapAndRead(basicCore.wbu.debug.get.isJump)
    debugInfoDpiC.jumpTarget := tapAndRead(basicCore.wbu.debug.get.jumpTarget)
    getGprDpiC.gpr := tapAndRead(basicCore.registerFile.debug.get)
  }
}
