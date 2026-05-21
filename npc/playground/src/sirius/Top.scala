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
    val bpu = Option.when(cfg.formal)(new IfuToBpuIO)
  })

  // val pcReg = Module(new PcReg)
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
  // pcReg.ifuIn :<>= ifu.exte.pcReg
  // pcReg.lsuIn :<>= lsu.exte.pcReg
  // pcReg.wbuIn :<>= wbu.exte.pcReg
  registerFile.iduIn :<>= idu.exte.regFile
  registerFile.wbuIn :<>= wbu.exte.regFlie
  csr.exuIn :<>= exu.exte.csr
  csr.wbuIn :<>= wbu.exte.csr
  ifu.exte.jumpTarget := Mux(wbu.exte.pcReg.isJump, wbu.exte.pcReg.target, lsu.exte.pcReg.target)
  val fencei = lsu.in.valid && lsu.in.bits.ctrl.lsuCtrl.isFlushIcache
  ifu.exte.fencei := fencei

  if (!cfg.formal) {
    val bpu = Module(
      new Bpu(
        btbIndexWidth = 2,
        btbTagWidth = 4,
        btbTargetWidth = 10,
        phtIndexWidth = 4,
        phtCounterWidth = 2
      )
    )
    bpu.ifuIn :<>= ifu.exte.bpu
    bpu.lsuIn :<>= lsu.exte.bpu
  } else {
    io.bpu.get :<>= ifu.exte.bpu
  }

  if (cfg.pipeline) {
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

    val stallIdu = Wire(Bool())
    val stallExu = Wire(Bool())
    val flushIfu = Wire(Bool())
    val flushIdu = Wire(Bool())
    val flushExu = Wire(Bool())
    val iduForwardBits = WireDefault(iduOut.bits)
    pipelineConnect(ifuOut, idu.in, flush = flushIfu)
    pipelineConnect(iduOut.map(_ => iduForwardBits), exu.in, stall = stallIdu, flush = flushIdu)
    pipelineConnect(exuOut, lsu.in, stall = stallExu, flush = flushExu)
    pipelineConnect(lsuOut, wbu.in)

    // RAW(GPR)
    val readRs1 = globalCtrl.globalCtrl.readRs1
    val rs1 = idu.exte.regFile.rAddr(0)
    val readRs2 = globalCtrl.globalCtrl.readRs2
    val rs2 = idu.exte.regFile.rAddr(1)
    MuxLookup
    case class StageRd(valid: Bool, isWriteBack: Bool, rd: UInt, forwardMap: Seq[(Bool, UInt)]) {
      require(forwardMap.length > 0)
      def conflict(rs: UInt): Bool = {
        valid && isWriteBack && (rd === rs)
      }
      def forward: (Bool, UInt) = {
        val conds = forwardMap.map(_._1)
        val forwardMayValid = conds.reduce(_ || _)
        val datas = forwardMap.map(_._2)
        val data = if (datas.length == 1) datas.head else Mux1H(conds, datas)
        (forwardMayValid, data)
      }
    }
    val stageRds = Seq(
      StageRd(
        exu.in.valid,
        exu.in.bits.ctrl.wbuCtrl.isWriteBackReg,
        exu.in.bits.iduPayload.idu.wAddr,
        Seq(
          (exu.out.valid &&
            (exu.in.bits.ctrl.wbuCtrl.writeBackSel === WriteBackSelEnum.alu.asUInt)) ->
            exu.out.bits.exuPayload.exu.aluOut
        )
      ),
      StageRd(
        lsu.in.valid,
        lsu.in.bits.ctrl.wbuCtrl.isWriteBackReg,
        lsu.in.bits.exuPayload.idu.wAddr,
        Seq(
          (lsu.in.valid &&
            (lsu.in.bits.ctrl.wbuCtrl.writeBackSel === WriteBackSelEnum.alu.asUInt)) ->
            lsu.in.bits.exuPayload.exu.aluOut
            // (lsu.out.valid &&
            //   (lsu.in.bits.ctrl.wbuCtrl.writeBackSel === WriteBackSelEnum.lsu.asUInt)) ->
            //   lsu.out.bits.lsuPayload.lsu.loadData
        )
      ),
      StageRd(
        wbu.in.valid,
        wbu.in.bits.ctrl.wbuCtrl.isWriteBackReg,
        wbu.in.bits.lsuPayload.idu.wAddr,
        Seq(
          (wbu.in.valid &&
            (wbu.in.bits.ctrl.wbuCtrl.writeBackSel === WriteBackSelEnum.alu.asUInt)) ->
            wbu.in.bits.lsuPayload.exu.aluOut,
          (wbu.in.valid &&
            (wbu.in.bits.ctrl.wbuCtrl.writeBackSel === WriteBackSelEnum.lsu.asUInt)) ->
            wbu.in.bits.lsuPayload.lsu.loadData
        )
      )
    )
    def decodeConflict(rs: UInt, readRs: Bool): (Bool, Bool, UInt) = {
      val stages = stageRds.map { s =>
        val conflict = s.conflict(rs) && readRs && rs =/= 0.U
        val (forwardMayValid, forwardData) = s.forward
        (conflict, forwardMayValid, forwardData)
      }
      val conflicts = stages.map(_._1)
      val forwardMayValids = stages.map(_._2)
      val forwardDatas = stages.map(_._3)
      val conflictStage = PriorityEncoderOH(conflicts)
      val forwardValid = (VecInit(conflictStage).asUInt & VecInit(forwardMayValids).asUInt).orR
      val forwardData = Mux1H(conflictStage, forwardDatas)
      (conflicts.reduce(_ || _), forwardValid, forwardData)
    }
    val (rs1Conflict, rs1ForwardValid, rs1ForwardData) = decodeConflict(rs1, readRs1)
    val (rs2Conflict, rs2ForwardValid, rs2ForwardData) = decodeConflict(rs2, readRs2)
    val isRawGpr = (rs1Conflict && !rs1ForwardValid) || (rs2Conflict && !rs2ForwardValid)
    iduForwardBits.iduPayload.idu.rs1Data := Mux(
      rs1ForwardValid,
      rs1ForwardData,
      iduOut.bits.iduPayload.idu.rs1Data
    )
    iduForwardBits.iduPayload.idu.rs2Data := Mux(
      rs2ForwardValid,
      rs2ForwardData,
      iduOut.bits.iduPayload.idu.rs2Data
    )

    // RAW(CSR)
    case class StageCsr(valid: Bool, isWriteBackCsr: Bool, check: Bool, imm: UInt, addr: UInt)
    val stageCsrs = Seq(
      StageCsr(
        lsu.in.valid,
        lsu.in.bits.ctrl.wbuCtrl.isWriteBackCsr,
        lsu.in.bits.ctrl.wbuCtrl.isCsrWriteCheck,
        lsu.in.bits.exuPayload.idu.imm,
        lsu.in.bits.exuPayload.idu.csrAddr
      ),
      StageCsr(
        wbu.in.valid,
        wbu.in.bits.ctrl.wbuCtrl.isWriteBackCsr,
        wbu.in.bits.ctrl.wbuCtrl.isCsrWriteCheck,
        wbu.in.bits.lsuPayload.idu.imm,
        wbu.in.bits.lsuPayload.idu.csrAddr
      )
    )
    val rawCsr = stageCsrs.map { s =>
      val isZicsr = s.valid && s.isWriteBackCsr
      val stageWillWrite = isZicsr && !(s.check && s.imm === 0.U)
      val exuWillRead = exu.in.bits.ctrl.wbuCtrl.isWriteBackCsr
      val exuReadAddr = exu.in.bits.iduPayload.idu.csrAddr
      stageWillWrite && exuWillRead && s.addr === exuReadAddr
    }
      .reduce(_ || _)

    // Jump
    // case class StageJump(valid: Bool, isJump: Bool, isBranch: Bool, isJumpCsr: Bool, isTrap: Bool)
    // val stageJumps = Seq(
    //   StageJump(
    //     lsu.in.valid,
    //     lsu.in.bits.ctrl.wbuCtrl.isJump,
    //     lsu.in.bits.ctrl.wbuCtrl.isBranch,
    //     lsu.in.bits.ctrl.wbuCtrl.isJumpCsr,
    //     (lsu.in.valid && lsu.in.bits.exuPayload.trap.isTrap) ||
    //       (lsu.out.valid && lsu.out.bits.lsuPayload.trap.isTrap)
    //   ),
    //   StageJump(
    //     wbu.in.valid,
    //     wbu.in.bits.ctrl.wbuCtrl.isJump,
    //     wbu.in.bits.ctrl.wbuCtrl.isBranch,
    //     wbu.in.bits.ctrl.wbuCtrl.isJumpCsr,
    //     wbu.in.valid && wbu.in.bits.lsuPayload.trap.isTrap
    //   )
    // )
    // val mayJump = stageJumps
    //   .map(s => s.isTrap || (s.valid && (s.isJump || s.isBranch || s.isJumpCsr)))
    //   .reduce(_ || _)

    // Pipeline ctrl
    flushIfu := wbu.exte.pcReg.isJump || lsu.exte.pcReg.isJump
    flushIdu := wbu.exte.pcReg.isJump || lsu.exte.pcReg.isJump
    flushExu := wbu.exte.pcReg.isJump
    ifu.exte.flush := wbu.exte.pcReg.isJump || lsu.exte.pcReg.isJump

    stallIdu := isRawGpr
    stallExu := rawCsr || lsu.exte.pcReg.isJump
    exu.exte.stall := stallExu

    // Debug
    if (cfg.perf) {
      val stopFlag = wbu.in.bits.ctrl.wbuCtrl.isEbreak
      def perfPipeline(
        name:        String,
        thisInValid: Bool,
        nextInValid: Bool,
        nextInReady: Bool,
        cause:       Map[String, Bool] = Map.empty
      ) = {
        // val isStageStall = RegNext(thisInValid) && nextInReady && !nextInValid
        val isStageStall = RegNext(thisInValid) && !nextInValid
        PerfWhen(s"${name}TotalStallCyc", isStageStall, Some(stopFlag))
        cause.foreach { case (condName, cond) =>
          PerfWhen(
            s"${name}${condName}StallCyc",
            isStageStall && cond,
            Some(stopFlag)
          )
        }
      }

      perfPipeline(
        "ifu",
        !reset.asBool,
        idu.in.valid,
        idu.in.ready,
        Map("Flush" -> RegNext(wbu.exte.pcReg.isJump || lsu.exte.pcReg.isJump))
      )
      perfPipeline(
        "idu",
        idu.in.valid,
        exu.in.valid,
        exu.in.ready,
        Map(
          "Flush" -> RegNext(wbu.exte.pcReg.isJump || lsu.exte.pcReg.isJump),
          "RawGpr" -> (isRawGpr || RegNext(isRawGpr))
        )
      )
      perfPipeline(
        "exu",
        exu.in.valid,
        lsu.in.valid,
        lsu.in.ready,
        Map(
          "Flush" -> RegNext(wbu.exte.pcReg.isJump || lsu.exte.pcReg.isJump),
          "RawCsr" -> (rawCsr || RegNext(rawCsr))
          // "MayJump" -> (mayJump || RegNext(mayJump))
        )
      )
      perfPipeline(
        "lsu",
        lsu.in.valid,
        wbu.in.valid,
        wbu.in.ready,
        Map("Flush" -> RegNext(wbu.exte.pcReg.isJump || lsu.exte.pcReg.isJump))
      )

      PerfWhen("totalJump", wbu.exte.pcReg.isJump || lsu.exte.pcReg.isJump, Some(stopFlag))

      import rvspeccore.checker._
      implicit val XLEN = cfg.xlen
      case class InstTypeCounter(valid: Bool, inst: UInt)
      val instTypeCounters = Seq(
        InstTypeCounter(
          idu.in.valid,
          idu.in.bits.ifuPayload.ifu.inst
        ),
        InstTypeCounter(
          exu.in.valid,
          exu.in.bits.iduPayload.ifu.inst
        ),
        InstTypeCounter(
          lsu.in.valid,
          lsu.in.bits.exuPayload.ifu.inst
        ),
        InstTypeCounter(
          wbu.in.valid,
          wbu.in.bits.lsuPayload.ifu.inst
        )
      )
      val instTypes = Map(
        "IRegImm" -> RVI.regImm,
        "IRegReg" -> RVI.regReg,
        "IControl" -> RVI.control,
        "ILoadStore" -> RVI.loadStore,
        "IOther" -> RVI.other,
        "ZicsrReg" -> RVZicsr.reg,
        "ZicsrImm" -> RVZicsr.imm,
        "Zifencei" -> RVZifencei.fence_i
      )
      instTypes.foreach { case (name, fn) =>
        PerfWhen(
          s"type${name}",
          idu.in.fire && fn.apply(idu.in.bits.ifuPayload.ifu.inst),
          Some(stopFlag)
        )
        PerfWhen(
          s"type${name}Cyc",
          instTypeCounters.map(s => s.valid && fn.apply(s.inst)).reduce(_ || _),
          Some(stopFlag)
        )
      }
    }
  } else {
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

  override val desiredName = "ysyx_26010008"
  withModulePrefix(cfg.modulePrefix.getOrElse("")) {

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
      val ebreakSignal = wbuIn.bits.ctrl.wbuCtrl.isEbreak
      ebreaks.foreach { e => drive(e.get) := ebreakSignal }

      val debugInfoDpiC = Module(new DebugInfoDpiC)
      val getGprDpiC = Module(new GetGprDpiC)
      debugInfoDpiC.isEbreak := ebreakSignal
      debugInfoDpiC.pc := wbuIn.bits.lsuPayload.ifu.pc
      // debugInfoDpiC.pcRaw := tapAndRead(basicCore.pcReg.debug.get.pc)
      debugInfoDpiC.pcRaw := cfg.pcInit.U
      // debugInfoDpiC.dnpc := pcReg.debug.get.dnpc
      debugInfoDpiC.inst := wbuIn.bits.lsuPayload.ifu.inst
      debugInfoDpiC.wbuValid := tapAndRead(basicCore.wbu.debug.get.valid)
      debugInfoDpiC.isJump := tapAndRead(basicCore.wbu.debug.get.isJump)
      debugInfoDpiC.jumpTarget := tapAndRead(basicCore.wbu.debug.get.jumpTarget)
      getGprDpiC.gpr := tapAndRead(basicCore.registerFile.debug.get)
    }
  }
}
