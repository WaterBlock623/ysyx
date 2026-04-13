package sirius

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

// Trap
class TrapIO(implicit private val cfg: CoreConfig) extends Bundle {
  val isTrap = Output(Bool())
  val cause = Output(UInt(cfg.mxlen.W))
}

// 数据载荷
class IfuPayload(implicit private val cfg: CoreConfig) extends Bundle {
  val trap = new TrapIO
  val ifu = new Bundle {
    val pc = UInt(cfg.xlen.W)
    val inst = UInt(cfg.xlen.W)
  }
}

class IduPayload(implicit private val cfg: CoreConfig) extends IfuPayload {
  val idu = new Bundle {
    val rs1Data = UInt(cfg.xlen.W)
    val rs2Data = UInt(cfg.xlen.W)
    val wAddr = UInt(cfg.registerAddrWidth.W)
    val imm = UInt(cfg.xlen.W)
    val csrAddr = UInt(12.W)
  }
}

class ExuPayload(implicit private val cfg: CoreConfig) extends IduPayload {
  val exu = new Bundle {
    val aluOut  = UInt(cfg.xlen.W)
    val jumpTarget = UInt(cfg.xlen.W)
    val csrData = UInt(cfg.mxlen.W)
  }
}

class LsuPayload(implicit private val cfg: CoreConfig) extends ExuPayload {
  val lsu = new Bundle {
    val loadData = UInt(cfg.xlen.W)
  }
}

// 控制信号
class WbuCtrl(implicit private val cfg: CoreConfig) extends Bundle {
  val wbuCtrl = new CtrlSignals().wb
  val debugCtrl = Option.when(cfg.isDebug)(new CtrlSignals().debug)
}

class LsuCtrl(implicit private val cfg: CoreConfig) extends WbuCtrl {
  val lsuCtrl = new CtrlSignals().ls
}

class ExuCtrl(implicit private val cfg: CoreConfig) extends LsuCtrl {
  val exuCtrl = new CtrlSignals().ex
}

// class IfuCtrl(implicit private val cfg: CoreConfig) extends Bundle {
//   val ifuCtrl = new CtrlSignals().if
// }

// IO
class IfuToIduIO(implicit private val cfg: CoreConfig) extends Bundle {
  val ifuPayload = Output(new IfuPayload)
}

class IduToExuIO(implicit private val cfg: CoreConfig) extends Bundle {
  val iduPayload = Output(new IduPayload)
  val ctrl = Output(new ExuCtrl)
}

class ExuToLsuIO(implicit private val cfg: CoreConfig) extends Bundle {
  val exuPayload = Output(new ExuPayload)
  val ctrl = Output(new LsuCtrl)
}

class LsuToWbuIO(implicit private val cfg: CoreConfig) extends Bundle {
  val lsuPayload = Output(new LsuPayload)
  val ctrl = Output(new WbuCtrl)
}

// 访问外部
class IfuToPcRegIO(implicit private val cfg: CoreConfig) extends Bundle {
  val pc = Input(UInt(cfg.xlen.W))
}

class IfuToMemIO(implicit private val cfg: CoreConfig) extends Bundle {
  val reqValid = Output(Bool())
  val reqReady = Input(Bool())
  val respValid = Input(Bool())
  val respReady = Output(Bool())
  val rAddr = Output(UInt(cfg.xlen.W))
  val rData = Input(UInt(cfg.xlen.W))
}

class IduToRegFileIO(implicit private val cfg: CoreConfig) extends Bundle {
  val rAddr = Output(Vec(2, UInt(cfg.registerAddrWidth.W)))
  val rData = Input(Vec(2, UInt(cfg.xlen.W)))
}

class ExuToCsrIO(implicit private val cfg: CoreConfig) extends Bundle {
  val rAddr = Output(UInt(12.W)) 
  val rData = Input(UInt(cfg.mxlen.W))
}

class LsuToMemIO(implicit private val cfg: CoreConfig) extends Bundle {
  val reqValid = Output(Bool())
  val reqReady = Input(Bool())
  val respValid = Input(Bool())
  val respReady = Output(Bool())
  val addr = Output(UInt(cfg.xlen.W))
  val rData = Input(UInt(cfg.xlen.W))
  val wData = Output(UInt(cfg.xlen.W))
  val wMask = Output(UInt((cfg.xlen >> 3).W))
  val wEn   = Output(Bool())
}

class WbuToRegFileIO(implicit private val cfg: CoreConfig) extends Bundle {
  val wEn   = Output(Bool())
  val wAddr = Output(UInt(cfg.registerAddrWidth.W))
  val wData = Output(UInt(cfg.xlen.W))
}

class WbuToPcRegIO(implicit private val cfg: CoreConfig) extends Bundle {
  val isJump = Output(Bool())
  val target = Output(UInt(cfg.xlen.W))
  val wEn = Output(Bool())
}

class WbuToCsrIO(implicit private val cfg: CoreConfig) extends Bundle {
  val wEn = Output(Bool())
  val wAddr = Output(UInt(12.W)) 
  val wData = Output(UInt(cfg.mxlen.W))
  val pc = Output(UInt(cfg.xlen.W))
  val isTrap = Output(Bool())
  val causeNum = Output(UInt(cfg.mxlen.W))
  val mtvec = Input(UInt(cfg.mxlen.W))
  val mepc = Input(UInt(cfg.mxlen.W))
}
