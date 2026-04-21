package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.MuxLookup
import chisel3.util.Fill
import chisel3.experimental.dataview._
import chisel3.util.Decoupled

// 解析imm
class ImmParser(
  implicit private val cfg: CoreConfig)
    extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(cfg.xlen.W))
    val instType = Input(UInt(InstTypeEnum.getWidth.W))
    val imm = Output(UInt(cfg.xlen.W))
  })

  val inst = io.inst(31, 0)

  val immTypeI = Fill(cfg.xlen - 11, inst(31)) ## inst(30, 20)
  val immTypeS = Fill(cfg.xlen - 11, inst(31)) ## inst(30, 25) ## inst(11, 7)
  val immTypeB =
    Fill(cfg.xlen - 12, inst(31)) ## inst(7) ## inst(30, 25) ##
      inst(11, 8) ## 0.U(1.W)
  val immTypeU = Fill(cfg.xlen - 31, inst(31)) ## inst(30, 12) ## 0.U(12.W)
  val immTypeJ =
    Fill(cfg.xlen - 20, inst(31)) ## inst(19, 12) ## inst(20) ## inst(
      30,
      21
    ) ## 0.U(1.W)
  val immTypeZicsr = inst(19, 15).pad(cfg.xlen)

  io.imm := MuxLookup(io.instType, immTypeI)(
    Seq(
      InstTypeEnum.I.asUInt -> immTypeI,
      InstTypeEnum.S.asUInt -> immTypeS,
      InstTypeEnum.B.asUInt -> immTypeB,
      InstTypeEnum.U.asUInt -> immTypeU,
      InstTypeEnum.J.asUInt -> immTypeJ,
      InstTypeEnum.Zicsr.asUInt -> immTypeZicsr
    )
  )
}

// 指令译码
class InstDecoder(
  implicit private val cfg: CoreConfig)
    extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(cfg.xlen.W))
    val ctrlSignals = Output(new CtrlSignals())
  })

  val decodeCollector = InstDecodeCollector()
  val decodeTable =
    new DecodeTable(decodeCollector.allPatterns, decodeCollector.allFields)
  val decodeResult = decodeTable.decode(io.inst)
  // 连接输出Bundle
  decodeCollector.allFields.foreach { f =>
    io.ctrlSignals.elements(f.stage).asInstanceOf[Bundle].elements(f.name) :=
      decodeResult(f.asInstanceOf[DecodeField[_, _ <: Data]])
  }
}

class Idu(
  implicit private val cfg: CoreConfig)
    extends Module {
  val exte = IO(new Bundle {
    val regFile = new IduToRegFileIO
    val globalCtrl = new GlobalCtrl
    val debugEbreak = Option.when(cfg.isDebug)(Input(Bool()))
  })
  val in = IO(Flipped(Decoupled(new IfuToIduIO)))
  val out = IO(Decoupled(new IduToExuIO))

  // DecoupledIO
  in.ready := out.ready
  out.valid := in.valid
  val inBits = in.bits
  val outBits = out.bits

  outBits.iduPayload.viewAsSupertype(new IfuPayload) := inBits.ifuPayload

  val inst = inBits.ifuPayload.ifu.inst
  // rs1
  exte.regFile.rAddr(0) := inst(19, 15)
  outBits.iduPayload.idu.rs1Data := exte.regFile.rData(0)
  // rs2
  exte.regFile.rAddr(1) := inst(24, 20)
  outBits.iduPayload.idu.rs2Data := exte.regFile.rData(1)
  // rd
  outBits.iduPayload.idu.wAddr := inst(11, 7)

  // ctrl
  val instDecoder = Module(new InstDecoder())
  instDecoder.io.inst := inst
  val ctrl = instDecoder.io.ctrlSignals
  // if (cfg.isDebug) {
  //   outBits.ctrl.debugCtrl.get := ctrl.debug
  // }
  outBits.ctrl.exuCtrl := ctrl.ex
  outBits.ctrl.lsuCtrl := ctrl.ls
  outBits.ctrl.wbuCtrl := ctrl.wb
  exte.globalCtrl.globalCtrl := ctrl.global

  when(!inBits.ifuPayload.trap.isTrap) {
    outBits.iduPayload.trap.isTrap := ctrl.wb.isEcall || ctrl.wb.isEbreak
    outBits.iduPayload.trap.cause := Mux(ctrl.wb.isEcall, 11.U(cfg.mxlen.W), 3.U(cfg.mxlen.W))
  }

  // imm
  val immParser = Module(new ImmParser())
  immParser.io.inst := inst
  immParser.io.instType := ctrl.id.instType
  outBits.iduPayload.idu.imm := immParser.io.imm

  // csr
  outBits.iduPayload.idu.csrAddr := inst(31, 20)

  InstTypeEnum.allWithNames.foreach { case (typ, name) =>
    PerfWhen(
      "type" + name,
      (ctrl.id.instType === typ.asUInt) && out.fire,
      exte.debugEbreak
    )
    PerfWhen(
      "type" + name + "Cyc",
      (ctrl.id.instType === typ.asUInt) && in.valid,
      exte.debugEbreak
    )
  }
}
