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
  val reset = IO(Input(Reset()))
  val inst = IO(Flipped(new IfuToMemIO))
  val ls = IO(Flipped(new LsuToMemIO))
  private val memAddrMsb = cfg.memoryAddrWidth - 1
  private val maskMsb = (cfg.xlen >> 3) - 1
  private val maskZero = 32 - (cfg.xlen >> 3)
  setInline(
    "MemDpiC.sv",
    s"""
import "DPI-C" function int dpic_pmem_read(input int raddr);
import "DPI-C" function void dpic_pmem_write(
  input int waddr, input int wdata, input int wmask);
module MemDpiC(
  input clock,
  input reset,

  input [$memAddrMsb:0] inst_rAddr,
  output reg [31:0] inst_rData,
  input inst_reqValid,
  output reg inst_reqReady,
  output reg inst_respValid,
  input inst_respReady,

  input [$memAddrMsb:0] ls_addr,
  output reg [31:0] ls_rData,
  input [31:0]  ls_wData,
  input [$maskMsb:0] ls_wMask,
  input ls_reqValid,
  output reg ls_respValid,
  input ls_wEn);

always @(posedge clock) begin
 ls_rData <= (ls_reqValid && !ls_wEn) ? dpic_pmem_read(ls_addr) : ${cfg.xlen}'b0;
 if (ls_reqValid && ls_wEn) begin
   dpic_pmem_write(ls_addr, ls_wData, {$maskZero'b0, ls_wMask});
 end
 ls_respValid <= ls_reqValid;
end


reg [$memAddrMsb:0] internal_inst_rData;
reg [$memAddrMsb:0] tmp_inst_rData;
reg tmp_inst_reqReady;
reg tmp_inst_respValid;
reg inst_state;
reg inst_next_state;
parameter WAIT_REQ = 1'b0, WAIT_READ = 1'b1;

always @(*) begin
  inst_next_state = WAIT_REQ;
  case (inst_state)
    WAIT_REQ: inst_next_state = inst_reqValid ? WAIT_READ : WAIT_REQ;
    WAIT_READ: inst_next_state = inst_respReady ? WAIT_REQ : WAIT_READ;
  endcase
end

always @(posedge clock) begin
  if (reset) begin
    inst_state <= WAIT_REQ;
  end else begin
    inst_state <= inst_next_state;
  end
end

always @(posedge clock) begin
  if (inst_state == WAIT_REQ && inst_next_state == WAIT_READ) begin
    internal_inst_rData <= dpic_pmem_read(inst_rAddr);
  end
end

assign tmp_inst_rData = tmp_inst_respValid ? internal_inst_rData : ${cfg.xlen}'b0;
assign tmp_inst_reqReady = inst_reqValid;
assign tmp_inst_respValid = inst_state == WAIT_READ;

assign inst_reqReady = tmp_inst_reqReady;
assign inst_respValid = tmp_inst_respValid;
assign inst_rData = tmp_inst_rData;

// gated_delay #(
//  .WIDTH(1),
//  .DELAY(5)
// ) u_gdelay_inst_reqReady (
//  .clock(clock),
//  .reset(reset),
//  .in(tmp_inst_reqReady),
//  .trigger(tmp_inst_reqReady),
//  .out(inst_reqReady)
// );
// delay_module #(
//  .WIDTH(1),
//  .DELAY(5)
// ) u_gdelay_inst_respValid (
//  .clock(clock),
//  .reset(reset),
//  .in(tmp_inst_respValid),
//  .out(inst_respValid)
// );
/*
delay_module #(
 .WIDTH(32),
 .DELAY(5)
) u_delay_inst_rData (
 .clock(clock),
 .reset(reset),
 .in(tmp_inst_rData),
 .out(inst_rData)
);
delay_module #(
 .WIDTH(1),
 .DELAY(5)
) u_delay_inst_reqReady (
 .clock(clock),
 .reset(reset),
 .in(tmp_inst_reqReady),
 .out(inst_reqReady)
);
delay_module #(
 .WIDTH(1),
 .DELAY(5)
) u_delay_inst_respValid (
 .clock(clock),
 .reset(reset),
 .in(tmp_inst_respValid),
 .out(inst_respValid)
);
*/

endmodule

module gated_delay #(
  parameter WIDTH = 32,
  parameter DELAY = 5
)(
  input  wire                    clock,
  input  wire                    reset,
  input  wire                    trigger,
  input  wire [WIDTH-1:0]   in,
  output wire [WIDTH-1:0]   out
);

  reg [7:0] count;
  reg is_active;

  always @(posedge clock) begin
    if (reset) begin
      count <= 0;
      is_active <= 1'b0;
    end else if (trigger && !is_active) begin
      if (count < DELAY) begin
        count <= count + 1'b1;
        is_active <= 1'b0;
      end else begin
        is_active <= 1'b1;
      end
    end else if (!trigger) begin
      count <= 0;
      is_active <= 1'b0;
    end
  end

  assign out = (is_active) ? in : {WIDTH{1'b0}};

endmodule

module delay_module #(
  parameter WIDTH = 32,
  parameter DELAY = 5
)(
  input  wire              clock,
  input  wire              reset,
  input  wire [WIDTH-1:0]  in,
  output wire [WIDTH-1:0]  out
);

  if (DELAY <= 0) begin
      assign out = in;
  end 
  else begin
    reg [WIDTH-1:0] delay_pipeline [0:DELAY-1];

    integer i;
    always @(posedge clock) begin
      if (reset) begin
        for (i = 0; i < DELAY; i = i + 1) begin
          delay_pipeline[i] <= {WIDTH{1'b0}};
        end
      end 
      else begin
        delay_pipeline[0] <= in;
        
        for (i = 1; i < DELAY; i = i + 1) begin
          delay_pipeline[i] <= delay_pipeline[i-1];
        end
      end
    end

    assign out = delay_pipeline[DELAY-1];
  end

endmodule
        """
  )
}

// class MemDpiC(
//   implicit private val cfg: CoreConfig)
//     extends ExtModule {
//   val clock = IO(Input(Clock()))
//   val reset = IO(Input(Reset()))
//   val inst = IO(Flipped(new IfuToMemIO))
//   val ls = IO(Flipped(new LsuToMemIO))
//   private val memAddrMsb = cfg.memoryAddrWidth - 1
//   private val maskMsb = (cfg.xlen >> 3) - 1
//   private val maskZero = 32 - (cfg.xlen >> 3)
//   setInline(
//     "MemDpiC.sv",
//     s"""|import "DPI-C" function int dpic_pmem_read(input int raddr);
//         |import "DPI-C" function void dpic_pmem_write(
//         |  input int waddr, input int wdata, input int wmask);
//         |module MemDpiC(
//         |  input clock,
//         |  input reset,
//         |  input [$memAddrMsb:0] inst_rAddr,
//         |  output reg [31:0] inst_rData,
//         |  input [$memAddrMsb:0] ls_addr,
//         |  output reg [31:0] ls_rData,
//         |  input [31:0]  ls_wData,
//         |  input [$maskMsb:0] ls_wMask,
//         |  input ls_reqValid,
//         |  output ls_respValid,
//         |  input ls_wEn);
//         |
//         |reg [7:0] lfsr_delay_cycles;
//         |always @(posedge clock) begin
//         | if (reset)
//         |   lfsr_delay_cycles <= 8'h2B;
//         | else
//         |   lfsr_delay_cycles <= {lfsr_delay_cycles[6:0], lfsr_delay_cycles[7] ^ lfsr_delay_cycles[5] ^ lfsr_delay_cycles[4] ^ lfsr_delay_cycles[3]};
//         |end
//         |
//         |reg [7:0] delay_cnt;
//         |reg [7:0] delay_cycles;
//         |reg is_busy;
//         |reg [31:0] pending_rdata;
//         |
//         |always @(posedge clock) begin
//         |    if (reset) begin
//         |        delay_cnt <= 0;
//         |        is_busy <= 0;
//         |        ls_respValid <= 0;
//         |    end else if (ls_reqValid && !is_busy) begin
//         |        delay_cycles <= lfsr_delay_cycles;
//         |        is_busy <= 1;
//         |        delay_cnt <= 1;
//         |        ls_respValid <= 0;
//         |        if (!ls_wEn) pending_rdata <= dpic_pmem_read(ls_addr);
//         |        if (ls_wEn) dpic_pmem_write(ls_addr, ls_wData, {$maskZero'b0, ls_wMask});
//         |    end else if (is_busy) begin
//         |        if (delay_cnt >= delay_cycles) begin
//         |            ls_respValid <= 1;
//         |            ls_rData <= pending_rdata;
//         |            is_busy <= 0;
//         |            delay_cnt <= 0;
//         |        end else begin
//         |            delay_cnt <= delay_cnt + 1;
//         |            ls_respValid <= 0;
//         |        end
//         |    end else begin
//         |        ls_respValid <= 0;
//         |    end
//         |end
//         |
//         |always @(posedge clock) begin
//         |  inst_rData = dpic_pmem_read(inst_rAddr);
//         |end
//         |endmodule
//         |""".stripMargin
//   )
// }

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
  implicit private val cfg:  CoreConfig,
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
    memDpiC.reset := reset
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
