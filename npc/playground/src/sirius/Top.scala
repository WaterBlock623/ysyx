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

class Top(
  implicit private val cfg: CoreConfig)
    extends Module {
  val ebreakDpiC = Module(new EbreakDpiC)
  val memDpiC = Module(new MemDpiC)
  
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

  ebreakDpiC.isEbreak := idu.out.ctrl.debugCtrl.isEbreak
  memDpiC.inst :<>= ifu.exte.mem
  memDpiC.ls :<>= lsu.exte.mem

  pcReg.ifuIn :<>= ifu.exte.pcReg
  pcReg.wbuIn :<>= wbu.exte.pcReg
  registerFile.iduIn :<>= idu.exte.regFile
  registerFile.wbuIn :<>= wbu.exte.regFlie
  idu.in :<>= ifuOut
  exu.in :<>= iduOut
  lsu.in :<>= exuOut
  wbu.in :<>= lsuOut
}
