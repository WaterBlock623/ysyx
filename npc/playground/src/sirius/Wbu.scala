package sirius

import chisel3._
import chisel3.util.MuxLookup
import chisel3.util.Decoupled
import chisel3.util.Counter
import rvspeccore.checker.RVZicsr

// 控制pc跳转和gpr读写
class Wbu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val exte = IO(new Bundle {
    val pcReg = new WbuToPcRegIO
    val regFlie = new WbuToRegFileIO
    val csr = new WbuToCsrIO
    val debugEbreak = Option.when(cfg.isDebug)(Input(Bool()))
  })
  val in = IO(Flipped(Decoupled(new LsuToWbuIO)))
  val debug = Option.when(cfg.isDebug)(IO(Output(new Bundle {
    val valid = Bool()
    val isJump = Bool()
    val jumpTarget = UInt(cfg.xlen.W)
  })))

  // DecoupledIO
  DecoupledFsm(false, in)
  in.ready := true.B
  val inBits = in.bits

  val ctrl = inBits.ctrl.wbuCtrl
  val pcReg = exte.pcReg
  val regFile = exte.regFlie
  val imm = inBits.lsuPayload.idu.imm
  val aluOut = inBits.lsuPayload.exu.aluOut
  val csrData = inBits.lsuPayload.exu.csrData

  // csr作为跳转地址
  val csrJumpTarget = MuxLookup(inBits.ctrl.wbuCtrl.jumpTargetSel, exte.csr.mepc)(
    Seq(
      JumpTargetSelEnum.mtvec.asUInt -> exte.csr.mtvec,
      JumpTargetSelEnum.mepc.asUInt -> exte.csr.mepc
    )
  )

  // pc
  val normalJumpTarget = inBits.lsuPayload.exu.jumpTarget
  pcReg.target := Mux(
    inBits.lsuPayload.trap.isTrap,
    exte.csr.mtvec,
    Mux(ctrl.isFromCsr, csrJumpTarget, normalJumpTarget)
  )
  pcReg.isJump := ctrl.isJump || ctrl.isFromCsr || (ctrl.isBranch && aluOut(
    0
  )) || inBits.lsuPayload.trap.isTrap
  pcReg.wEn := in.valid

  // gpr
  regFile.wAddr := inBits.lsuPayload.idu.wAddr
  val pc = inBits.lsuPayload.ifu.pc
  val loadData = inBits.lsuPayload.lsu.loadData
  regFile.wEn := ctrl.isWriteBackReg && in.valid && !inBits.lsuPayload.trap.isTrap
  regFile.wData := MuxLookup(ctrl.writeBackSel, aluOut)(
    Seq(
      WriteBackSelEnum.alu.asUInt -> aluOut,
      WriteBackSelEnum.imm.asUInt -> imm,
      WriteBackSelEnum.staticNextPc.asUInt -> (pc + 4.U),
      WriteBackSelEnum.lsu.asUInt -> loadData,
      WriteBackSelEnum.csr.asUInt -> csrData
    )
  )

  // csr
  val csr = exte.csr
  csr.wEn := inBits.ctrl.wbuCtrl.isWriteBackCsr &&
    (imm.orR || !inBits.ctrl.wbuCtrl.isCsrWriteCheck) && in.valid && !inBits.lsuPayload.trap.isTrap
  csr.wAddr := inBits.lsuPayload.idu.csrAddr
  csr.wData := inBits.lsuPayload.exu.aluOut

  csr.pc := pc
  csr.isTrap := in.valid && inBits.lsuPayload.trap.isTrap
  csr.causeNum := inBits.lsuPayload.trap.cause

  if (cfg.formal) {
    implicit val XLEN: Int = cfg.xlen

    when(exte.pcReg.wEn && exte.pcReg.isJump) {
      assume(exte.pcReg.target(1, 0) === 0.U)
    }

    import rvspeccore.core.RVConfig
    val rvConfig = RVConfig(
      XLEN = cfg.xlen,
      extensions = "ZifenceiZicsr",
      fakeExtensions = "",
      initValue = Map(
        "pc" -> s"h${cfg.pcInit.toString(16)}",
        "mstatus" -> "h1800"
      ),
      functions = Seq(),
      formal = Seq("CheckMem", "ArbitraryRegFile")
    )

    import rvspeccore.checker._
    val checker = Module(new CheckerWithState(enableReg = false)(rvConfig))
    checker.io.instCommit.valid := RegNext(in.valid && !reset.asBool)
    checker.io.instCommit.excp := RegNext(in.bits.lsuPayload.trap.isTrap)
    checker.io.instCommit.inst := RegNext(in.bits.lsuPayload.ifu.inst)
    checker.io.instCommit.pc := RegNext(in.bits.lsuPayload.ifu.pc)
    checker.io.instCommit.npc := DontCare

    ConnectHelper.setChecker(checker)(cfg.xlen, rvConfig)
    ConnectHelper.makePrivilegeSource()(rvConfig) := DontCare

    val memAccessWire = rvspeccore.checker.ConnectHelper.makeMemSource()(cfg.xlen)
    val formalSig = in.bits.lsuPayload.lsu.formal.get
    memAccessWire.read.valid := RegNext(formalSig.read.valid && in.valid && !reset.asBool)
    memAccessWire.read.addr := RegNext(formalSig.read.addr)
    memAccessWire.read.data := RegNext(formalSig.read.data)
    memAccessWire.read.memWidth := RegNext(formalSig.read.memWidth)

    memAccessWire.write.valid := RegNext(formalSig.write.valid && in.valid && !reset.asBool)
    memAccessWire.write.addr := RegNext(formalSig.write.addr)
    memAccessWire.write.data := RegNext(formalSig.write.data)
    memAccessWire.write.memWidth := RegNext(formalSig.write.memWidth)

    // val cnt = Counter(checker.io.instCommit.valid, 127)
    // when (cnt._1 === 1.U) {
    //   assert(false.B)
    // }
  }

  if (cfg.isDebug) {
    dontTouch(debug.get)
    debug.get.valid := in.valid
    debug.get.isJump := pcReg.wEn && pcReg.isJump
    debug.get.jumpTarget := pcReg.target
  }

  PerfWhen(
    "totalCyc",
    true.B,
    exte.debugEbreak
  )
  PerfWhen(
    "totalInst",
    in.valid,
    exte.debugEbreak
  )
}
