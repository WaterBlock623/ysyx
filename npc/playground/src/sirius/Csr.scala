package sirius

import chisel3._
import chisel3.util._

class CsrIO(implicit private val cfg: CoreConfig) extends Bundle {
  val wEn = Bool()
  val wData = UInt(cfg.mxlen.W)
  val rData = Flipped(UInt(cfg.mxlen.W))
}

class CsrParent(implicit private val cfg: CoreConfig) extends Module {
  val csrIO = IO(Flipped(new CsrIO))
}

class CsrParent32(implicit private val cfg: CoreConfig) extends Module {
  val csrIOHi = IO(Flipped(new CsrIO))
  val csrIOLo = IO(Flipped(new CsrIO))
}

class CsrMcycle32(implicit private val cfg: CoreConfig) 
  extends CsrParent32 {
  val cycleCntReg = Reg(new Bundle {
    val hi = UInt(32.W)
    val lo = UInt(32.W)
  })
  when (csrIOHi.wEn) {
    cycleCntReg.hi := csrIOHi.wData
  }
  when (csrIOLo.wEn) {
    cycleCntReg.lo := csrIOLo.wData
  }
  when (!(csrIOHi.wEn || csrIOLo.wEn)) {
    cycleCntReg := 
      (cycleCntReg.asUInt + 1.U).asTypeOf(chiselTypeOf(cycleCntReg))
  }
  csrIOHi.rData := cycleCntReg.hi
  csrIOLo.rData := cycleCntReg.lo
}

class CsrMvendorid(implicit private val cfg: CoreConfig) extends CsrParent {
  csrIO.rData := cfg.mvendorid.U
}

class CsrMarchid(implicit private val cfg: CoreConfig) extends CsrParent {
  csrIO.rData := cfg.marchid.U
}

class Csr(implicit private val cfg: CoreConfig, 
  implicit private val ucfg: UnitConfig) extends Module {
  val exuIn = IO(Flipped(new ExuToCsrIO))
  val wbuIn = IO(Flipped(new WbuToCsrIO))

  // // 实例化
  // val csrs = ucfg.csrMap
  //   .map { case (csrSel: String, csr: (() => CsrParent)) => 
  //     (csrSel -> Module(csr()))
  //   }
  // val csrs32 = ucfg.csr32Map
  //   .map { case ((csrSelHi: String, csrSelLo: String), csr: (() => CsrParent32)) => 
  //     ((csrSelHi, csrSelLo) -> Module(csr()))
  //   }
  // // 连接写使能 选择输出
  // val rData = VecInit(
  //   csrs.map { case (csrSel: String, csr: CsrParent) => 
  //     val en = io.csrSel === csrSel.U
  //     csr.csrIO.wEn := en && io.wEn
  //     csr.csrIO.rData & Fill(csr.csrIO.rData.getWidth, en.asUInt)
  //   }.toSeq ++
  //   csrs32.flatMap { case ((csrSelHi: String, csrSelLo), csr: CsrParent32) => 
  //     val enHi = io.csrSel === csrSelHi.U
  //     val enLo = io.csrSel === csrSelLo.U
  //     csr.csrIOHi.wEn := enHi && io.wEn
  //     csr.csrIOLo.wEn := enLo && io.wEn
  //     Seq(
  //       csr.csrIOHi.rData & Fill(csr.csrIOHi.rData.getWidth, enHi.asUInt),
  //       csr.csrIOLo.rData & Fill(csr.csrIOLo.rData.getWidth, enLo.asUInt)
  //     )
  //   }.toSeq
  // ).reduceTree(_ | _)

  // 实例化
  val csrs = ucfg.csrMap.map { case (addr, gen) => addr -> Module(gen()) }
  val csrs32 = ucfg.csr32Map.map { case ((hi, lo), gen) => ((hi, lo) -> Module(gen())) }

  // 读数据Lut
  val readMap = csrs.map { case (addr, mod) => 
    addr.U -> mod.csrIO.rData 
  }.toSeq ++ csrs32.flatMap { case ((hi, lo), mod) => 
    Seq(hi.U -> mod.csrIOHi.rData, lo.U -> mod.csrIOLo.rData)
  }.toSeq

  exuIn.rData := MuxLookup(exuIn.rAddr, 0.U)(readMap)

  // WriteRaw Set Clear
  // val wData = MuxLookup(io.wOpCode, io.wOperand)(Seq(
  //     CsrWOpCode.write.asUInt -> io.wOperand,
  //     CsrWOpCode.set.asUInt -> (io.rData | io.wOperand),
  //     CsrWOpCode.clear.asUInt -> (io.rData & ~io.wOperand),
  //   ))

  // 写使能 写数据
  csrs.foreach { case (addr, mod) => 
    mod.csrIO.wEn   := wbuIn.wEn && (wbuIn.wAddr === addr.U)
    mod.csrIO.wData := wbuIn.wData
  }
  
  csrs32.foreach { case ((hi, lo), mod) =>
    mod.csrIOHi.wEn   := wbuIn.wEn && (wbuIn.wAddr === hi.U)
    mod.csrIOHi.wData := wbuIn.wData
    
    mod.csrIOLo.wEn   := wbuIn.wEn && (wbuIn.wAddr === lo.U)
    mod.csrIOLo.wData := wbuIn.wData
  }
}
