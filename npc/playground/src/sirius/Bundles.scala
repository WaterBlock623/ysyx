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

// class VerilogAxi4LiteIO(val busWidth: Int = 32) extends Bundle {
//   val AWVALID = Output(Bool())
//   val AWREADY = Input(Bool())
//   val AWADDR = Output(UInt(busWidth.W))
//   // val AWPROT
//
//   val WVALID = Output(Bool())
//   val WREADY = Input(Bool())
//   val WDATA = Output(UInt(busWidth.W))
//   val WSTRB = Output(UInt((busWidth >> 3).W))
//
//   val BVALID = Input(Bool())
//   val BREADY = Output(Bool())
//   val BRESP = Input(UInt(2.W))
//
//   val ARVALID = Output(Bool())
//   val ARREADY = Input(Bool())
//   val ARADDR = Output(UInt(busWidth.W))
//   // val ARPROT
//
//   val RVALID = Input(Bool())
//   val RREADY = Output(Bool())
//   val RDATA = Input(UInt(busWidth.W))
//   val RRESP = Input(UInt(2.W))
// }
// object VerilogAxi4LiteIO {
//   implicit val view: DataView[Axi4LiteIO, VerilogAxi4LiteIO] = 
//     Axi4LiteIO.view.invert(axi4LiteIO => new VerilogAxi4LiteIO(axi4LiteIO.busWidth))
// }
//
// class Axi4LiteIO(val busWidth: Int = 32) extends Bundle {
//   val aw = Decoupled(new Bundle {
//     val addr = Output(UInt(busWidth.W))
//     // val prot
//   })
//
//   val w = Decoupled(new Bundle {
//     val data = Output(UInt(busWidth.W))
//     val strb = Output(UInt((busWidth >> 3).W))
//   })
//
//   val b = Flipped(Decoupled(Flipped(new Bundle {
//     val resp = Input(UInt(2.W))
//   })))
//
//   val ar = Decoupled(new Bundle {
//     val addr = Output(UInt(busWidth.W))
//     // val prot
//   })
//
//   val r = Flipped(Decoupled(Flipped(new Bundle {
//     val data = Input(UInt(busWidth.W))
//     val resp = Input(UInt(2.W))
//   })))
// }
// object Axi4LiteIO {
//   implicit val view: DataView[VerilogAxi4LiteIO, Axi4LiteIO] = DataView(
//     verilogAxi4LiteIO => new Axi4LiteIO(verilogAxi4LiteIO.busWidth),
//     _.AWVALID -> _.aw.valid,
//     _.AWREADY -> _.aw.ready,
//     _.AWADDR -> _.aw.bits.addr,
//     // _.AWPROT -> aw.bits.port,
//
//     _.WVALID -> _.w.valid,
//     _.WREADY -> _.w.ready,
//     _.WDATA -> _.w.bits.data,
//     _.WSTRB -> _.w.bits.strb,
//
//     _.BVALID -> _.b.valid,
//     _.BREADY -> _.b.ready,
//     _.BRESP -> _.b.bits.resp,
//
//     _.ARVALID -> _.ar.valid,
//     _.ARREADY -> _.ar.ready,
//     _.ARADDR -> _.ar.bits.addr,
//     // _.ARPROT -> _.ar.bits.prot,
//
//     _.RVALID -> _.r.valid,
//     _.RREADY -> _.r.ready,
//     _.RDATA -> _.r.bits.data,
//     _.RRESP -> _.r.bits.resp
//   )
// }

class YsyxSocAxi4IO(val busWidth: Int = 32) extends Bundle {
  val awready = Input(Bool())
  val awvalid = Output(Bool())
  val awaddr = Output(UInt(busWidth.W))
  val awid = Output(UInt(4.W))
  val awlen = Output(UInt(8.W))
  val awsize = Output(UInt(3.W))
  val awburst = Output(UInt(2.W))
  // val awprot

  val wready = Input(Bool())
  val wvalid = Output(Bool())
  val wdata = Output(UInt(busWidth.W))
  val wstrb = Output(UInt((busWidth >> 3).W))
  val wlast = Output(Bool())

  val bready = Output(Bool())
  val bvalid = Input(Bool())
  val bresp = Input(UInt(2.W))
  val bid = Input(UInt(4.W))

  val arready = Input(Bool())
  val arvalid = Output(Bool())
  val araddr = Output(UInt(busWidth.W))
  val arid = Output(UInt(4.W))
  val arlen = Output(UInt(8.W))
  val arsize = Output(UInt(3.W))
  val arburst = Output(UInt(2.W))
  // val arprot

  val rready = Output(Bool())
  val rvalid = Input(Bool())
  val rresp = Input(UInt(2.W))
  val rdata = Input(UInt(busWidth.W))
  val rlast = Input(Bool())
  val rid = Input(UInt(4.W))
}
object YsyxSocAxi4IO {
  implicit val view: DataView[Axi4IO, YsyxSocAxi4IO] = 
    Axi4IO.view.invert(axi4IO => new YsyxSocAxi4IO(axi4IO.busWidth))
}

class Axi4IO(val busWidth: Int = 32) extends Bundle {
  val aw = Decoupled(new Bundle {
    val addr = Output(UInt(busWidth.W))
    val id = Output(UInt(4.W))
    val len = Output(UInt(8.W))
    val size = Output(UInt(3.W))
    val burst = Output(UInt(2.W))
  // val prot
  })

  val w = Decoupled(new Bundle {
    val data = Output(UInt(busWidth.W))
    val strb = Output(UInt((busWidth >> 3).W))
    val last = Output(Bool())
  })

  val b = Flipped(Decoupled(Flipped(new Bundle {
    val resp = Input(UInt(2.W))
    val id = Input(UInt(4.W))
  })))

  val ar = Decoupled(new Bundle {
    val addr = Output(UInt(busWidth.W))
    val id = Output(UInt(4.W))
    val len = Output(UInt(8.W))
    val size = Output(UInt(3.W))
    val burst = Output(UInt(2.W))
    // val prot
  })

  val r = Flipped(Decoupled(Flipped(new Bundle {
    val resp = Input(UInt(2.W))
    val data = Input(UInt(busWidth.W))
    val last = Input(Bool())
    val id = Input(UInt(4.W))
  })))
}
object Axi4IO {
  implicit val view: DataView[YsyxSocAxi4IO, Axi4IO] = DataView(
    ysyxSocAxi4IO => new Axi4IO(ysyxSocAxi4IO.busWidth),
    _.awready -> _.aw.ready,
    _.awvalid -> _.aw.valid,
    _.awaddr -> _.aw.bits.addr,
    _.awid -> _.aw.bits.id,
    _.awlen -> _.aw.bits.len,
    _.awsize -> _.aw.bits.size,
    _.awburst -> _.aw.bits.burst,
    // _.awprot -> aw.bits.port,

    _.wready -> _.w.ready,
    _.wvalid -> _.w.valid,
    _.wdata -> _.w.bits.data,
    _.wstrb -> _.w.bits.strb,
    _.wlast -> _.w.bits.last,

    _.bready -> _.b.ready,
    _.bvalid -> _.b.valid,
    _.bresp -> _.b.bits.resp,
    _.bid -> _.b.bits.id,

    _.arready -> _.ar.ready,
    _.arvalid -> _.ar.valid,
    _.araddr -> _.ar.bits.addr,
    _.arid -> _.ar.bits.id,
    _.arlen -> _.ar.bits.len,
    _.arsize -> _.ar.bits.size,
    _.arburst -> _.ar.bits.burst,
    // _.arprot -> _.ar.bits.prot,

    _.rready -> _.r.ready,
    _.rvalid -> _.r.valid,
    _.rresp -> _.r.bits.resp,
    _.rdata -> _.r.bits.data,
    _.rlast -> _.r.bits.last,
    _.rid -> _.r.bits.id
  )
}

