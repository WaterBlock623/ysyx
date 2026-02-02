package sirius

import chisel3._
import chisel3.util._

class EbreakDpiC extends ExtModule {
  val isEbreak = IO(Input(Bool()))
  setInline(
    "EbreakDpiC.sv",
    """|import "DPI-C" function void check_ebreak(input int is_ebreak);
       |module EbreakDpiC(input isEbreak);
       |always @(*) begin
       | check_ebreak({31'b0, isEbreak});
       |end
       |endmodule
    """.stripMargin
  )
}

class MemDpiC(
  implicit private val cfg: CoreConfig)
    extends ExtModule {
  val inst = IO(Flipped(new IfuToMemIO))
  val ls = IO(Flipped(new LsuToMemIO))
  private val memAddrMsb = cfg.memoryAddrWidth - 1
  private val maskMsb = (cfg.xlen >> 3) - 1
  private val maskZero = 8 - (cfg.xlen >> 3)
  setInline(
    "MemDpiC.sv",
    s"""|import "DPI-C" function int pmem_read(input int raddr);
        |import "DPI-C" function void pmem_write(
        |  input int waddr, input int wdata, input byte wmask);
        |module MemDpiC(
        |  input [$memAddrMsb:0] inst_rAddr, 
        |  output reg [31:0] inst_rData, 
        |  input [$memAddrMsb:0] ls_rAddr, 
        |  output reg [31:0] ls_rData, 
        |  input [$memAddrMsb:0] ls_wAddr,
        |  input [31:0]  ls_wData,
        |  input [$maskMsb:0] ls_wMask,
        |  input ls_valid, 
        |  input ls_wEn);
        |always @(*) begin
        |  if (ls_valid) begin
        |    ls_rData = pmem_read(ls_rAddr);
        |    if (ls_wEn) begin
        |      pmem_write(ls_wAddr, ls_wData, {$maskZero'b0, ls_wMask});
        |    end
        |  end
        |  else begin
        |    ls_rData = 0;
        |  end
        |end
        |always @(*) begin
        |  inst_rData = pmem_read(inst_rAddr);
        |end
        |endmodule
        |""".stripMargin
  )
}

class GetRetDpiC extends ExtModule {
  val a0 = IO(Input(UInt(32.W)))
  setInline(
    "GetRetDpiC.sv",
    """|import "DPI-C" function void get_ret(input int a0);
       |module GetRetDpiC(input [31:0] a0);
       |always @(*) begin
       |  get_ret(a0);
       |end
       |endmodule
    """.stripMargin
  )
}

class GetGPRDpiC(implicit private val cfg: CoreConfig) extends ExtModule {
  val regNum = cfg.registerNum
  val xlen = cfg.xlen

  val gpr = IO(Input(Vec(regNum, UInt(xlen.W))))
  
  val portDecls = (0 until regNum).map(i => s"input [${xlen-1}:0] gpr_$i").mkString(", ")
  val portNames = (0 until regNum).map(i => s"gpr_$i").mkString(", ")

  setInline(
    "GetGPRDpiC.sv",
    s"""|import "DPI-C" function void sync_gprs(input logic [${xlen-1}:0] values []);
        |module GetGPRDpiC($portDecls);
        |  always @(*) begin
        |    sync_gprs('{$portNames});
        |  end
        |endmodule
     """.stripMargin
  )
}

class Top(
  implicit private val cfg: CoreConfig)
    extends Module {
  
  val pcReg = Module(new PcReg)
  val registerFile = Module(new RegisterFile)
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
    val ebreakDpiC = Module(new EbreakDpiC)
    val memDpiC = Module(new MemDpiC)
    val getRetDpiC = Module(new GetRetDpiC)
    ebreakDpiC.isEbreak := idu.out.ctrl.debugCtrl.get.isEbreak
    memDpiC.inst :<>= ifu.exte.mem
    memDpiC.ls :<>= lsu.exte.mem
    getRetDpiC.a0 := registerFile.debug.get(10)
  }

  pcReg.ifuIn :<>= ifu.exte.pcReg
  pcReg.wbuIn :<>= wbu.exte.pcReg
  registerFile.iduIn :<>= idu.exte.regFile
  registerFile.wbuIn :<>= wbu.exte.regFlie
  idu.in :<>= ifuOut
  exu.in :<>= iduOut
  lsu.in :<>= exuOut
  wbu.in :<>= lsuOut
}
