package sirius

import chisel3._
import chisel3.util._
import chisel3.experimental.dataview._

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
//     s"""
// import "DPI-C" function int dpic_pmem_read(input int raddr);
// import "DPI-C" function void dpic_pmem_write(
//   input int waddr, input int wdata, input int wmask);
// module MemDpiC(
//   input clock,
//   input reset,
//
//   input [$memAddrMsb:0] inst_rAddr,
//   output reg [31:0] inst_rData,
//   input inst_reqValid,
//   output reg inst_reqReady,
//   output reg inst_respValid,
//   input inst_respReady,
//
//   input [$memAddrMsb:0] ls_addr,
//   output reg [31:0] ls_rData,
//   input [31:0]  ls_wData,
//   input [$maskMsb:0] ls_wMask,
//   input ls_reqValid,
//   output ls_reqReady,
//   output reg ls_respValid,
//   input reg ls_respReady,
//   input ls_wEn);
//
// parameter WAIT_REQ = 1'b0, WAIT_READ = 1'b1;
//
// // always @(posedge clock) begin
// //  ls_rData <= (ls_reqValid && !ls_wEn) ? dpic_pmem_read(ls_addr) : ${cfg.xlen}'b0;
// //  if (ls_reqValid && ls_wEn) begin
// //    dpic_pmem_write(ls_addr, ls_wData, {$maskZero'b0, ls_wMask});
// //  end
// //  ls_respValid <= ls_reqValid;
// // end
// reg [$memAddrMsb:0] internal_ls_rData;
// reg [$memAddrMsb:0] tmp_ls_rData;
// reg tmp_ls_reqReady;
// reg tmp_ls_respValid;
// reg ls_state;
// reg ls_next_state;
//
// always @(*) begin
//   ls_next_state = WAIT_REQ;
//   case (ls_state)
//     WAIT_REQ: ls_next_state = ls_reqValid ? WAIT_READ : WAIT_REQ;
//     WAIT_READ: ls_next_state = ls_respReady ? WAIT_REQ : WAIT_READ;
//   endcase
// end
//
// always @(posedge clock) begin
//   if (reset) begin
//     ls_state <= WAIT_REQ;
//   end else begin
//     ls_state <= ls_next_state;
//   end
// end
//
// always @(posedge clock) begin
//   if (ls_state == WAIT_REQ && ls_next_state == WAIT_READ) begin
//     if (ls_wEn) begin
//       dpic_pmem_write(ls_addr, ls_wData, {$maskZero'b0, ls_wMask});
//     end else begin
//       internal_ls_rData <= dpic_pmem_read(ls_addr);
//     end
//   end
// end
//
// assign tmp_ls_rData = tmp_ls_respValid ? internal_ls_rData : ${cfg.xlen}'b0;
// assign tmp_ls_reqReady = ls_reqValid;
// assign tmp_ls_respValid = ls_state == WAIT_READ;
//
// assign ls_reqReady = tmp_ls_reqReady;
// assign ls_respValid = tmp_ls_respValid;
// assign ls_rData = tmp_ls_rData;
//
//
// reg [$memAddrMsb:0] internal_inst_rData;
// reg [$memAddrMsb:0] tmp_inst_rData;
// reg tmp_inst_reqReady;
// reg tmp_inst_respValid;
// reg inst_state;
// reg inst_next_state;
//
// always @(*) begin
//   inst_next_state = WAIT_REQ;
//   case (inst_state)
//     WAIT_REQ: inst_next_state = inst_reqValid ? WAIT_READ : WAIT_REQ;
//     WAIT_READ: inst_next_state = inst_respReady ? WAIT_REQ : WAIT_READ;
//   endcase
// end
//
// always @(posedge clock) begin
//   if (reset) begin
//     inst_state <= WAIT_REQ;
//   end else begin
//     inst_state <= inst_next_state;
//   end
// end
//
// always @(posedge clock) begin
//   if (inst_state == WAIT_REQ && inst_next_state == WAIT_READ) begin
//     internal_inst_rData <= dpic_pmem_read(inst_rAddr);
//   end
// end
//
// assign tmp_inst_rData = tmp_inst_respValid ? internal_inst_rData : ${cfg.xlen}'b0;
// assign tmp_inst_reqReady = inst_reqValid;
// assign tmp_inst_respValid = inst_state == WAIT_READ;
//
// assign inst_reqReady = tmp_inst_reqReady;
// assign inst_respValid = tmp_inst_respValid;
// assign inst_rData = tmp_inst_rData;
//
// // gated_delay #(
// //  .WIDTH(1),
// //  .DELAY(5)
// // ) u_gdelay_inst_reqReady (
// //  .clock(clock),
// //  .reset(reset),
// //  .in(tmp_inst_reqReady),
// //  .trigger(tmp_inst_reqReady),
// //  .out(inst_reqReady)
// // );
// // delay_module #(
// //  .WIDTH(1),
// //  .DELAY(5)
// // ) u_gdelay_inst_respValid (
// //  .clock(clock),
// //  .reset(reset),
// //  .in(tmp_inst_respValid),
// //  .out(inst_respValid)
// // );
// /*
// delay_module #(
//  .WIDTH(32),
//  .DELAY(5)
// ) u_delay_inst_rData (
//  .clock(clock),
//  .reset(reset),
//  .in(tmp_inst_rData),
//  .out(inst_rData)
// );
// delay_module #(
//  .WIDTH(1),
//  .DELAY(5)
// ) u_delay_inst_reqReady (
//  .clock(clock),
//  .reset(reset),
//  .in(tmp_inst_reqReady),
//  .out(inst_reqReady)
// );
// delay_module #(
//  .WIDTH(1),
//  .DELAY(5)
// ) u_delay_inst_respValid (
//  .clock(clock),
//  .reset(reset),
//  .in(tmp_inst_respValid),
//  .out(inst_respValid)
// );
// */
//
// endmodule
//
// module gated_delay #(
//   parameter WIDTH = 32,
//   parameter DELAY = 5
// )(
//   input  wire                    clock,
//   input  wire                    reset,
//   input  wire                    trigger,
//   input  wire [WIDTH-1:0]   in,
//   output wire [WIDTH-1:0]   out
// );
//
//   reg [7:0] count;
//   reg is_active;
//
//   always @(posedge clock) begin
//     if (reset) begin
//       count <= 0;
//       is_active <= 1'b0;
//     end else if (trigger && !is_active) begin
//       if (count < DELAY) begin
//         count <= count + 1'b1;
//         is_active <= 1'b0;
//       end else begin
//         is_active <= 1'b1;
//       end
//     end else if (!trigger) begin
//       count <= 0;
//       is_active <= 1'b0;
//     end
//   end
//
//   assign out = (is_active) ? in : {WIDTH{1'b0}};
//
// endmodule
//
// module delay_module #(
//   parameter WIDTH = 32,
//   parameter DELAY = 5
// )(
//   input  wire              clock,
//   input  wire              reset,
//   input  wire [WIDTH-1:0]  in,
//   output wire [WIDTH-1:0]  out
// );
//
//   if (DELAY <= 0) begin
//       assign out = in;
//   end 
//   else begin
//     reg [WIDTH-1:0] delay_pipeline [0:DELAY-1];
//
//     integer i;
//     always @(posedge clock) begin
//       if (reset) begin
//         for (i = 0; i < DELAY; i = i + 1) begin
//           delay_pipeline[i] <= {WIDTH{1'b0}};
//         end
//       end 
//       else begin
//         delay_pipeline[0] <= in;
//
//         for (i = 1; i < DELAY; i = i + 1) begin
//           delay_pipeline[i] <= delay_pipeline[i-1];
//         end
//       end
//     end
//
//     assign out = delay_pipeline[DELAY-1];
//   end
//
// endmodule
//         """
//   )
// }

// class MemDpiC(
//   implicit private val cfg: CoreConfig)
//     extends ExtModule {
//   val clock = IO(Input(Clock()))
//   val reset = IO(Input(Reset()))
//   val inst = IO(Flipped(new VerilogAxi4LiteIO))
//   val ls = IO(Flipped(new LsuToMemIO))
//
//   private val memAddrMsb = cfg.memoryAddrWidth - 1
//   private val maskMsb = (cfg.xlen >> 3) - 1
//   private val maskZero = 32 - (cfg.xlen >> 3)
//
//   private val delayProb = 0
//   private val maxDelayCycle = 30
//
//   setInline(
//     "MemDpiC.sv",
//     s"""
// import "DPI-C" function int dpic_pmem_read(input int raddr);
// import "DPI-C" function void dpic_pmem_write(
//   input int waddr, input int wdata, input int wmask);
//
// module MemDpiC(
//   input clock,
//   input reset,
//
//   input [$memAddrMsb:0] inst_rAddr,
//   output[31:0] inst_rData,
//   input inst_reqValid,
//   output inst_reqReady,
//   output inst_respValid,
//   input inst_respReady,
//
//   input[$memAddrMsb:0] ls_addr,
//   output [31:0] ls_rData,
//   input[31:0]  ls_wData,
//   input [$maskMsb:0] ls_wMask,
//   input ls_reqValid,
//   output ls_reqReady,
//   output ls_respValid,
//   input ls_respReady,
//   input ls_wEn);
//
// reg [31:0] internal_ls_rData;
// reg ls_state; // 0: IDLE, 1: WAIT_RESP
// integer ls_delay_cnt;
//
// assign ls_reqReady = (ls_state == 0) && (ls_delay_cnt == 0);
// assign ls_respValid = (ls_state == 1) && (ls_delay_cnt == 0);
// assign ls_rData = internal_ls_rData;
//
// always @(posedge clock) begin
//   if (reset) begin
//     ls_state <= 0;
//     ls_delay_cnt <= 0;
//   end else begin
//     if (ls_state == 0) begin
//       if (ls_delay_cnt > 0) begin
//         ls_delay_cnt <= ls_delay_cnt - 1;
//       end else if (ls_reqValid && ls_reqReady) begin
//         ls_state <= 1;
//         ls_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//
//         if (ls_wEn) begin
//           dpic_pmem_write(ls_addr, ls_wData, {${maskZero}'b0, ls_wMask});
//         end else begin
//           internal_ls_rData <= dpic_pmem_read(ls_addr);
//         end
//       end
//     end else begin
//       if (ls_delay_cnt > 0) begin
//         ls_delay_cnt <= ls_delay_cnt - 1;
//       end else if (ls_respValid && ls_respReady) begin
//         ls_state <= 0;
//         ls_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//       end
//     end
//   end
// end
//
// reg [31:0] internal_inst_rData;
// reg inst_state;
// integer inst_delay_cnt;
//
// assign inst_reqReady = (inst_state == 0) && (inst_delay_cnt == 0);
// assign inst_respValid = (inst_state == 1) && (inst_delay_cnt == 0);
// assign inst_rData = internal_inst_rData;
//
// always @(posedge clock) begin
//   if (reset) begin
//     inst_state <= 0;
//     inst_delay_cnt <= 0;
//   end else begin
//     if (inst_state == 0) begin
//       if (inst_delay_cnt > 0) begin
//         inst_delay_cnt <= inst_delay_cnt - 1;
//       end else if (inst_reqValid && inst_reqReady) begin
//         inst_state <= 1;
//         inst_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//         internal_inst_rData <= dpic_pmem_read(inst_rAddr);
//       end
//     end else begin
//       if (inst_delay_cnt > 0) begin
//         inst_delay_cnt <= inst_delay_cnt - 1;
//       end else if (inst_respValid && inst_respReady) begin
//         inst_state <= 0;
//         inst_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//       end
//     end
//   end
// end
//
// endmodule
// """
//   )
// }

class MemDpiC(
  implicit private val cfg: CoreConfig)
    extends ExtModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Reset()))
  val axi = IO(Flipped(new Axi4FlatIO))

  private val memAddrMsb = cfg.memoryAddrWidth - 1
  private val maskMsb = (cfg.xlen >> 3) - 1
  private val maskZero = 32 - (cfg.xlen >> 3)

  private val delayProb = 0
  private val maxDelayCycle = 30
  
  setInline(
    "MemDpiC.sv",
    s"""
import "DPI-C" function int dpic_pmem_read(input int raddr);
import "DPI-C" function void dpic_pmem_write(
  input int waddr, input int wdata, input int wmask);

module MemDpiC(
  input clock,
  input reset,

  input axi_awvalid,
  output reg axi_awready,
  input [$memAddrMsb:0] axi_awaddr,
  input [3:0] axi_awid,
  input [7:0] axi_awlen,
  input [2:0] axi_awsize,
  input [1:0] axi_awburst,

  input axi_wvalid,
  output reg axi_wready,
  input [31:0] axi_wdata,
  input [$maskMsb:0] axi_wstrb,
  input axi_wlast,

  output reg axi_bvalid,
  input axi_bready,
  output reg [1:0] axi_bresp,
  output reg [3:0] axi_bid,

  input axi_arvalid,
  output reg axi_arready,
  input [$memAddrMsb:0] axi_araddr,
  input [3:0] axi_arid,
  input [7:0] axi_arlen,
  input [2:0] axi_arsize,
  input [1:0] axi_arburst,

  output reg axi_rvalid,
  input axi_rready,
  output reg [31:0] axi_rdata,
  output reg [1:0] axi_rresp,
  output reg axi_rlast,
  output reg [3:0] axi_rid
);

reg [3:0] r_arid_reg;
reg [3:0] r_awid_reg;

assign axi_rresp = 0;
assign axi_bresp = 0;
assign axi_rlast = 1;
assign axi_rid   = r_arid_reg;
assign axi_bid   = r_awid_reg;

reg [31:0] internal_ls_rData;
reg read_state; // 0: IDLE, 1: WAIT_RESP
integer read_delay_cnt;

assign axi_arready = (read_state == 0) && (read_delay_cnt == 0);
assign axi_rvalid = (read_state == 1) && (read_delay_cnt == 0);
assign axi_rdata = axi_rvalid ? internal_ls_rData : ${cfg.xlen}'b0;

always @(posedge clock) begin
  if (reset) begin
    read_state <= 0;
    read_delay_cnt <= 0;
  end else begin
    if (read_state == 0) begin
      if (read_delay_cnt > 0) begin
        read_delay_cnt <= read_delay_cnt - 1;
      end else if (axi_arvalid && axi_arready) begin
        read_state <= 1;
        r_arid_reg <= axi_arid;
        /* verilator lint_off UNSIGNED */
        read_delay_cnt <= ($$urandom_range(0, 99) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
        internal_ls_rData <= dpic_pmem_read(axi_araddr);
      end
    end else begin
      if (read_delay_cnt > 0) begin
        read_delay_cnt <= read_delay_cnt - 1;
      end else if (axi_rvalid && axi_rready) begin
        read_state <= 0;
        /* verilator lint_off UNSIGNED */
        read_delay_cnt <= ($$urandom_range(0, 99) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
      end
    end
  end
end

reg write_state; // 0: IDLE, 1: WAIT_RESP
integer write_delay_cnt;

assign axi_awready = (write_state == 0) && (axi_awvalid && axi_wvalid) && (write_delay_cnt == 0);
assign axi_wready = axi_awready;
assign axi_bvalid = (write_state == 1) && (write_delay_cnt == 0);

always @(posedge clock) begin
  if (reset) begin
    write_state <= 0;
    write_delay_cnt <= 0;
  end else begin
    if (write_state == 0) begin
      if (write_delay_cnt > 0) begin
        write_delay_cnt <= write_delay_cnt - 1;
      end else if (axi_awvalid && axi_wvalid) begin
        write_state <= 1;
        r_awid_reg <= axi_awid;
        /* verilator lint_off UNSIGNED */
        write_delay_cnt <= ($$urandom_range(0, 99) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
        dpic_pmem_write(axi_awaddr, axi_wdata, {${maskZero}'b0, axi_wstrb});
      end
    end else begin
      if (write_delay_cnt > 0) begin
        write_delay_cnt <= write_delay_cnt - 1;
      end else if (axi_bvalid && axi_bready) begin
        write_state <= 0;
        /* verilator lint_off UNSIGNED */
        write_delay_cnt <= ($$urandom_range(0, 99) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
      end
    end
  end
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
//   val inst = IO(Flipped(new VerilogAxi4LiteIO))
//   // val ls = IO(Flipped(new LsuToMemIO))
//   val AXI = IO(Flipped(new VerilogAxi4LiteIO))
//
//   private val memAddrMsb = cfg.memoryAddrWidth - 1
//   private val maskMsb = (cfg.xlen >> 3) - 1
//   private val maskZero = 32 - (cfg.xlen >> 3)
//
//   private val delayProb = 50
//   private val maxDelayCycle = 30
//
//   setInline(
//     "MemDpiC.sv",
//     s"""
// import "DPI-C" function int dpic_pmem_read(input int raddr);
// import "DPI-C" function void dpic_pmem_write(
//   input int waddr, input int wdata, input int wmask);
//
// module MemDpiC(
//   input clock,
//   input reset,
//
//
//   input inst_AWVALID,
//   output reg inst_AWREADY,
//   input [$memAddrMsb:0] inst_AWADDR,
//
//   input inst_WVALID,
//   output reg inst_WREADY,
//   input [31:0] inst_WDATA,
//   input [$maskMsb:0] inst_WSTRB,
//
//   output reg inst_BVALID,
//   input inst_BREADY,
//   output reg [1:0] inst_BRESP,
//
//   input inst_ARVALID,
//   output reg inst_ARREADY,
//   input [$memAddrMsb:0] inst_ARADDR,
//
//   output reg inst_RVALID,
//   input inst_RREADY,
//   output reg [31:0] inst_RDATA,
//   output reg [1:0] inst_RRESP,
//
//
//   input AXI_AWVALID,
//   output reg AXI_AWREADY,
//   input [$memAddrMsb:0] AXI_AWADDR,
//
//   input AXI_WVALID,
//   output reg AXI_WREADY,
//   input [31:0] AXI_WDATA,
//   input [$maskMsb:0] AXI_WSTRB,
//
//   output reg AXI_BVALID,
//   input AXI_BREADY,
//   output reg [1:0] AXI_BRESP,
//
//   input AXI_ARVALID,
//   output reg AXI_ARREADY,
//   input [$memAddrMsb:0] AXI_ARADDR,
//
//   output reg AXI_RVALID,
//   input AXI_RREADY,
//   output reg [31:0] AXI_RDATA,
//   output reg [1:0] AXI_RRESP
// );
//
// assign AXI_RRESP = 0;
// reg [31:0] internal_ls_rData;
// reg read_state; // 0: IDLE, 1: WAIT_RESP
// integer read_delay_cnt;
//
// assign AXI_ARREADY= (read_state == 0) && (read_delay_cnt == 0);
// assign AXI_RVALID = (read_state == 1) && (read_delay_cnt == 0);
// assign AXI_RDATA = AXI_RVALID ? internal_ls_rData : ${cfg.xlen}'b0;
//
// always @(posedge clock) begin
//   if (reset) begin
//     read_state <= 0;
//     read_delay_cnt <= 0;
//   end else begin
//     if (read_state == 0) begin
//       if (read_delay_cnt > 0) begin
//         read_delay_cnt <= read_delay_cnt - 1;
//       end else if (AXI_ARVALID && AXI_ARREADY) begin
//         read_state <= 1;
//         /* verilator lint_off UNSIGNED */
//         read_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//         internal_ls_rData <= dpic_pmem_read(AXI_ARADDR);
//       end
//     end else begin
//       if (read_delay_cnt > 0) begin
//         read_delay_cnt <= read_delay_cnt - 1;
//       end else if (AXI_RVALID && AXI_RREADY) begin
//         read_state <= 0;
//         /* verilator lint_off UNSIGNED */
//         read_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//       end
//     end
//   end
// end
//
// assign AXI_BRESP = 0;
// reg write_state; // 0: IDLE, 1: WAIT_RESP
// integer write_delay_cnt;
//
// assign AXI_AWREADY = (write_state == 0) && (AXI_AWVALID && AXI_WVALID) && (write_delay_cnt == 0);
// assign AXI_WREADY = AXI_AWREADY;
// assign AXI_BVALID = (write_state == 1) && (write_delay_cnt == 0);
//
// always @(posedge clock) begin
//   if (reset) begin
//     write_state <= 0;
//     write_delay_cnt <= 0;
//   end else begin
//     if (write_state == 0) begin
//       if (write_delay_cnt > 0) begin
//         write_delay_cnt <= write_delay_cnt - 1;
//       end else if (AXI_AWVALID && AXI_WVALID) begin
//         write_state <= 1;
//         /* verilator lint_off UNSIGNED */
//         write_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//         dpic_pmem_write(AXI_AWADDR, AXI_WDATA, {${maskZero}'b0, AXI_WSTRB});
//       end
//     end else begin
//       if (write_delay_cnt > 0) begin
//         write_delay_cnt <= write_delay_cnt - 1;
//       end else if (AXI_BVALID && AXI_BREADY) begin
//         write_state <= 0;
//         /* verilator lint_off UNSIGNED */
//         write_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//       end
//     end
//   end
// end
//
//
// assign inst_AWREADY = 0;
// assign inst_WREADY = 0;
// assign inst_BVALID = 0;
// assign inst_BRESP = 0;
//
// assign inst_RRESP = 0;
//
// reg [31:0] internal_inst_rData;
// reg inst_state;
// integer inst_delay_cnt;
//
// assign inst_ARREADY = (inst_state == 0) && (inst_delay_cnt == 0);
// assign inst_RVALID = (inst_state == 1) && (inst_delay_cnt == 0);
// assign inst_RDATA = inst_RVALID ? internal_inst_rData : ${cfg.xlen}'b0;
//
// always @(posedge clock) begin
//   if (reset) begin
//     inst_state <= 0;
//     inst_delay_cnt <= 0;
//   end else begin
//     if (inst_state == 0) begin
//       if (inst_delay_cnt > 0) begin
//         inst_delay_cnt <= inst_delay_cnt - 1;
//       end else if (inst_ARVALID && inst_ARREADY) begin
//         inst_state <= 1;
//         inst_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//         internal_inst_rData <= dpic_pmem_read(inst_ARADDR);
//       end
//     end else begin
//       if (inst_delay_cnt > 0) begin
//         inst_delay_cnt <= inst_delay_cnt - 1;
//       end else if (inst_RVALID && inst_RREADY) begin
//         inst_state <= 0;
//         inst_delay_cnt <= ($$urandom_range(0, 100) < ${100-delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
//       end
//     end
//   end
// end
//
// endmodule
// """
//   )
// }


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
        |  reg [${xlen - 1}:0] temp_regs [$regNum] /* verilator public_flat */;
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

  val memBusArbiter = Module(new MemBusArbiter)
  val xbar = Module(new Xbar)
  val clintDevice = Module(new ClintDevice)
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

  if (cfg.ysyxsoc) {
    val io = IO(new Bundle {
      val interrupt = Input(Bool())
      val master = new Axi4FlatIO
      val slave = Flipped(new Axi4FlatIO)
    })
    0.U.asTypeOf(chiselTypeOf(io.slave)) :>= io.slave
    io.master :<>= xbar.out(0).viewAs[Axi4FlatIO]
  } else if (cfg.isDebug) {
    val memDpiC = Module(new MemDpiC)
    memDpiC.axi :<>= xbar.out(0).viewAs[Axi4FlatIO]
    memDpiC.clock := clock
    memDpiC.reset := reset
  }

  if (cfg.isDebug) {
    val debugInfoDpiC = Module(new DebugInfoDpiC)
    val getGprDpiC = Module(new GetGprDpiC)
    debugInfoDpiC.isEbreak := idu.out.bits.ctrl.debugCtrl.get.isEbreak
    debugInfoDpiC.pc := pcReg.debug.get.pc
    debugInfoDpiC.dnpc := pcReg.debug.get.dnpc
    debugInfoDpiC.inst := ifu.debug.get
    debugInfoDpiC.wbuValid := wbu.out.valid
    getGprDpiC.gpr := registerFile.debug.get
  }

  memBusArbiter.in(0) :<>= ifu.exte.mem
  memBusArbiter.in(1) :<>= lsu.exte.mem
  xbar.in :<>= memBusArbiter.out
  clintDevice.in :<>= xbar.out(1)
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
