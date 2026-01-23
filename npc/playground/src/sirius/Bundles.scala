package sirius

import chisel3._

// 数据载荷(递增)
class IfuPayload(implicit private val cfg: CoreConfig) extends Bundle {
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
  }
}

class ExuPayload(implicit private val cfg: CoreConfig) extends IduPayload {
  val exu = new Bundle {
    val aluOut  = UInt(cfg.xlen.W)
  }
}

class LsuPayload(implicit private val cfg: CoreConfig) extends ExuPayload {
  val lsu = new Bundle {
    val loadData = UInt(cfg.xlen.W)
  }
}

// 控制信号(递减/反向递增)
class WbuCtrl(implicit private val cfg: CoreConfig) extends Bundle {
  val wbuCtrl = new CtrlSignals().wb
}

class LsuCtrl(implicit private val cfg: CoreConfig) extends WbuCtrl {
  val lsuCtrl = new CtrlSignals().ls
}

class ExuCtrl(implicit private val cfg: CoreConfig) extends LsuCtrl {
  val exuCtrl = new CtrlSignals().ex
}

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

class IduToRegFileIO(implicit private val cfg: CoreConfig) extends Bundle {
  val rAddr = Output(Vec(2, UInt(cfg.registerAddrWidth.W)))
  val rData = Input(Vec(2, UInt(cfg.xlen.W)))
}

class IfuToMemIO(implicit private val cfg: CoreConfig) extends Bundle {
  val rAddr = Output(UInt(cfg.xlen.W))
  val rData = Input(UInt(cfg.xlen.W))
}

class LsuToMemIO(implicit private val cfg: CoreConfig) extends Bundle {
  val valid = Output(Bool())
  val rAddr = Output(UInt(cfg.xlen.W))
  val rData = Input(UInt(cfg.xlen.W))
  val wAddr = Output(UInt(cfg.xlen.W))
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
}

