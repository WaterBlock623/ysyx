package sirius

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

// 数据载荷
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
}

class LsuCtrl(implicit private val cfg: CoreConfig) extends WbuCtrl {
  val lsuCtrl = new CtrlSignals().ls
}

class ExuCtrl(implicit private val cfg: CoreConfig) extends LsuCtrl {
  val exuCtrl = new CtrlSignals().ex
  val debugCtrl = if (cfg.isDebug) Some(new CtrlSignals().debug) else None
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
class VerilogAxi4LiteIO(val busWidth: Int = 32) extends Bundle {
  val AWVALID = Output(Bool())
  val AWREADY = Input(Bool())
  val AWADDR = Output(UInt(busWidth.W))
  // val AWPROT

  val WVALID = Output(Bool())
  val WREADY = Input(Bool())
  val WDATA = Output(UInt(busWidth.W))
  val WSTRB = Output(UInt((busWidth >> 3).W))

  val BVALID = Input(Bool())
  val BREADY = Output(Bool())
  val BRESP = Input(UInt(2.W))

  val ARVALID = Output(Bool())
  val ARREADY = Input(Bool())
  val ARADDR = Output(UInt(busWidth.W))
  // val ARPROT

  val RVALID = Input(Bool())
  val RREADY = Output(Bool())
  val RDATA = Input(UInt(busWidth.W))
  val RRESP = Input(UInt(2.W))
}
object VerilogAxi4LiteIO {
  implicit val view: DataView[Axi4LiteIO, VerilogAxi4LiteIO] = 
    Axi4LiteIO.view.invert(axi4LiteIO => new VerilogAxi4LiteIO(axi4LiteIO.busWidth))
}

class Axi4LiteIO(val busWidth: Int = 32) extends Bundle {
  val aw = Decoupled(new Bundle {
    val addr = Output(UInt(busWidth.W))
    // val prot
  })

  val w = Decoupled(new Bundle {
    val data = Output(UInt(busWidth.W))
    val strb = Output(UInt((busWidth >> 8).W))
  })

  val b = Flipped(Decoupled(Flipped(new Bundle {
    val resp = Input(UInt(2.W))
  })))

  val ar = Decoupled(new Bundle {
    val addr = Output(UInt(busWidth.W))
    // val prot
  })

  val r = Flipped(Decoupled(Flipped(new Bundle {
    val data = Input(UInt(busWidth.W))
    val resp = Input(UInt(2.W))
  })))
}
object Axi4LiteIO {
  implicit val view: DataView[VerilogAxi4LiteIO, Axi4LiteIO] = DataView(
    verilogAxi4LiteIO => new Axi4LiteIO(verilogAxi4LiteIO.busWidth),
    _.AWVALID -> _.aw.valid,
    _.AWREADY -> _.aw.valid,
    _.AWADDR -> _.aw.bits.addr,
    // _.AWPROT -> aw.bits.port,

    _.WVALID -> _.w.valid,
    _.WREADY -> _.w.ready,
    _.WDATA -> _.w.bits.data,
    _.WSTRB -> _.w.bits.strb,

    _.BVALID -> _.b.valid,
    _.BREADY -> _.b.ready,
    _.BRESP -> _.b.bits.resp,

    _.ARVALID -> _.ar.valid,
    _.ARREADY -> _.ar.ready,
    _.ARADDR -> _.ar.bits.addr,
    // _.ARPROT -> _.ar.bits.prot,

    _.RVALID -> _.r.valid,
    _.RREADY -> _.r.ready,
    _.RDATA -> _.r.bits.data,
    _.RRESP -> _.r.bits.resp
  )
}

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
