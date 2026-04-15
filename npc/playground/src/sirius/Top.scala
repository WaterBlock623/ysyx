package sirius

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

class Top(
  implicit private val cfg:  CoreConfig,
  implicit private val ucfg: UnitConfig)
    extends Module {

  // val memBusArbiter = Module(new MemBusArbiter)
  // val xbar = Module(new Xbar)
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
    val debugInfoDpiC = Module(new DebugInfoDpiC)
    val getGprDpiC = Module(new GetGprDpiC)
    debugInfoDpiC.isEbreak := idu.out.bits.ctrl.debugCtrl.get.isEbreak
    debugInfoDpiC.pc := pcReg.debug.get.pc
    debugInfoDpiC.dnpc := pcReg.debug.get.dnpc
    debugInfoDpiC.inst := ifu.debug.get
    debugInfoDpiC.wbuValid := wbu.out.valid
    getGprDpiC.gpr := registerFile.debug.get
  }

  // memBusArbiter.in(0) :<>= ifu.exte.mem
  // memBusArbiter.in(1) :<>= lsu.exte.mem
  // xbar.in :<>= memBusArbiter.out
  xbar.io.in(0) :<>= lsu.exte.mem
  xbar.io.in(1) :<>= ifu.exte.mem
  clintDevice.in :<>= xbar.io.out(1)
  pcReg.ifuIn :<>= ifu.exte.pcReg
  pcReg.wbuIn :<>= wbu.exte.pcReg
  registerFile.iduIn :<>= idu.exte.regFile
  registerFile.wbuIn :<>= wbu.exte.regFlie
  csr.exuIn :<>= exu.exte.csr
  csr.wbuIn :<>= wbu.exte.csr
  ifu.exte.globalCtrl := globalCtrl

  if (cfg.pipeline) {
    def pipelineConnect[T <: Data](
      prevOut: DecoupledIO[T],
      thisIn:  DecoupledIO[T],
      stall:   Bool = false.B
    ) = {
      val ready = thisIn.ready || !thisIn.valid
      prevOut.ready := ready && !stall
      thisIn.bits := RegEnable(prevOut.bits, prevOut.fire)
      thisIn.valid := RegEnable(prevOut.valid && !stall, false.B, ready)
    }

    val stallIdu = Wire(Bool())
    val stallExu = Wire(Bool())
    pipelineConnect(ifuOut, idu.in)
    pipelineConnect(iduOut, exu.in, stallIdu)
    pipelineConnect(exuOut, lsu.in, stallExu)
    pipelineConnect(lsuOut, wbu.in)

    
  } else {
    idu.in :<>= ifuOut
    exu.in :<>= iduOut
    lsu.in :<>= exuOut
    wbu.in :<>= lsuOut
  }
}
