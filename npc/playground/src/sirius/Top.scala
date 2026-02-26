package sirius

import chisel3._
import chisel3.util._

class DebugInfoDpiC(
  implicit private val cfg: CoreConfig)
    extends ExtModule {
  val isEbreak = IO(Input(Bool()))
  val pc = IO(Input(UInt(cfg.xlen.W)))
  val dnpc = IO(Input(UInt(cfg.xlen.W)))
  val inst = IO(Input(UInt(cfg.xlen.W)))
  val wbuValid = IO(Input(Bool()))
  setInline(
    "DebugInfoDpiC.sv",
    s"""|import "DPI-C" function void set_debug_info(input int is_ebreak, 
        |  input int pc, input int dnpc, input int inst, input int wbu_valid
        |  );
        |module DebugInfoDpiC(input isEbreak, input [${cfg.xlen - 1}:0] pc, 
        |  input [${cfg.xlen - 1}:0] dnpc, input [${cfg.xlen - 1}:0] inst,
        |  input wbuValid
        |  );
        |always @(*) begin
        | set_debug_info({31'b0, isEbreak}, pc, dnpc, inst, {31'b0, wbuValid});
        |end
        |endmodule
    """.stripMargin
  )
}

class MemDpiC(
  implicit private val cfg: CoreConfig)
    extends ExtModule {
  val clock = IO(Input(Clock()))
  val inst = IO(Flipped(new IfuToMemIO))
  val ls = IO(Flipped(new LsuToMemIO))
  private val memAddrMsb = cfg.memoryAddrWidth - 1
  private val maskMsb = (cfg.xlen >> 3) - 1
  private val maskZero = 32 - (cfg.xlen >> 3)
  setInline(
    "MemDpiC.sv",
    s"""|import "DPI-C" function int dpic_pmem_read(input int raddr);
        |import "DPI-C" function void dpic_pmem_write(
        |  input int waddr, input int wdata, input int wmask);
        |module MemDpiC(
        |  input clock,
        |  input [$memAddrMsb:0] inst_rAddr, 
        |  output reg [31:0] inst_rData, 
        |  input [$memAddrMsb:0] ls_addr, 
        |  output reg [31:0] ls_rData, 
        |  input [31:0]  ls_wData,
        |  input [$maskMsb:0] ls_wMask,
        |  input ls_reqValid, 
        |  output ls_respValid, 
        |  input ls_wEn);
        |
        |always @(posedge clock) begin
        | ls_rData <= (ls_reqValid && !ls_wEn) ? dpic_pmem_read(ls_addr) : ${cfg.xlen}'b0;
        | if (ls_reqValid && ls_wEn) begin
        |   dpic_pmem_write(ls_addr, ls_wData, {$maskZero'b0, ls_wMask});
        | end
        | ls_respValid <= ls_reqValid;
        |end
        |
        |always @(posedge clock) begin
        |  inst_rData = dpic_pmem_read(inst_rAddr);
        |end
        |endmodule
        |""".stripMargin
  )
}

// class MemRegFile(
//   implicit private val cfg: CoreConfig)
//     extends Module {
//   val inst = IO(Flipped(new IfuToMemIO))
//   val ls = IO(Flipped(new LsuToMemIO))
//
//   val iMem = Mem(256, UInt(32.W))
//   inst.rData := iMem.read(inst.rAddr)
//
//   val lsMem = Mem(256, Vec(4, UInt(8.W)))
//   val lsMask = ls.wMask
//         .asTypeOf(Vec(4, Bool()))
//         .toSeq
//         // .map(Fill(8, _))
//         // .reduce(_ ## _)
//         // .asTypeOf(Vec(32, Bool()))
//   ls.rData := DontCare
//   when (ls.valid) {
//     when (ls.wEn) {
//       lsMem.write(ls.wAddr, ls.wData.asTypeOf(Vec(4, UInt(8.W))), lsMask)
//     } .otherwise {
//       ls.rData := lsMem.read(ls.rAddr).asUInt
//     }
//   }
// }

// class GetRetDpiC extends ExtModule {
//   val a0 = IO(Input(UInt(32.W)))
//   setInline(
//     "GetRetDpiC.sv",
//     """|import "DPI-C" function void get_ret(input int a0);
//        |module GetRetDpiC(input [31:0] a0);
//        |always @(*) begin
//        |  get_ret(a0);
//        |end
//        |endmodule
//     """.stripMargin
//   )
// }

class GetGprDpiC(
  implicit private val cfg: CoreConfig)
    extends ExtModule {
  private val regNum = cfg.registerNum
  private val xlen = cfg.xlen
  val gpr = IO(Input(Vec(regNum, UInt(xlen.W))))
  val portDecls =
    (0 until regNum).map(i => s"input [${xlen - 1}:0] gpr_$i").mkString(", ")
  val assignLogic =
    (0 until regNum).map(i => s"    temp_regs[$i] = gpr_$i;").mkString("\n")

  setInline(
    "GetGprDpiC.sv",
    s"""|
        |
        |module GetGprDpiC($portDecls);
        |  reg [${xlen - 1}:0] temp_regs [$regNum] /* verilator public */;
        |
        |  always @(*) begin
        |    $assignLogic
        |  end
        |endmodule
     """.stripMargin
  )
}

class Top(
  implicit private val cfg: CoreConfig,
  implicit private val ucfg: UnitConfig)
    extends Module {

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

  if (cfg.isDebug) {
    val debugInfoDpiC = Module(new DebugInfoDpiC)
    val memDpiC = Module(new MemDpiC)
    // val getRetDpiC = Module(new GetRetDpiC)
    val getGprDpiC = Module(new GetGprDpiC)

    debugInfoDpiC.isEbreak := idu.out.bits.ctrl.debugCtrl.get.isEbreak
    debugInfoDpiC.pc := pcReg.debug.get.pc
    debugInfoDpiC.dnpc := pcReg.debug.get.dnpc
    debugInfoDpiC.inst := ifu.debug.get
    debugInfoDpiC.wbuValid := wbu.out.valid
    memDpiC.inst :<>= ifu.exte.mem
    memDpiC.ls :<>= lsu.exte.mem
    memDpiC.clock := clock
    // getRetDpiC.a0 := registerFile.debug.get(10)
    getGprDpiC.gpr := registerFile.debug.get
  } else {
    // val memRegFile = Module(new MemRegFile)
    // memRegFile.inst :<>= ifu.exte.mem
    // memRegFile.ls :<>= lsu.exte.mem

    // val out = IO(Output(UInt(32.W)))
    // out := pcReg.ifuIn.pc ^ registerFile.iduIn.rData.reduce(_ ^ _) ^ memRegFile.inst.rData ^ memRegFile.ls.rData
    val io = IO(new Bundle {
      val inst = new IfuToMemIO
      val ls = new LsuToMemIO
    })
    io.inst :<>= ifu.exte.mem
    io.ls :<>= lsu.exte.mem
  }

  pcReg.ifuIn :<>= ifu.exte.pcReg
  pcReg.wbuIn :<>= wbu.exte.pcReg
  registerFile.iduIn :<>= idu.exte.regFile
  registerFile.wbuIn :<>= wbu.exte.regFlie
  csr.exuIn :<>= exu.exte.csr
  csr.wbuIn :<>= wbu.exte.csr
  idu.in :<>= ifuOut
  exu.in :<>= iduOut
  lsu.in :<>= exuOut
  wbu.in :<>= lsuOut
}
