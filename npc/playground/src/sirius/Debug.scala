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
        read_delay_cnt <= ($$urandom_range(0, 99) < ${100 - delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
        internal_ls_rData <= dpic_pmem_read(axi_araddr);
      end
    end else begin
      if (read_delay_cnt > 0) begin
        read_delay_cnt <= read_delay_cnt - 1;
      end else if (axi_rvalid && axi_rready) begin
        read_state <= 0;
        /* verilator lint_off UNSIGNED */
        read_delay_cnt <= ($$urandom_range(0, 99) < ${100 - delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
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
        write_delay_cnt <= ($$urandom_range(0, 99) < ${100 - delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
        dpic_pmem_write(axi_awaddr, axi_wdata, {${maskZero}'b0, axi_wstrb});
      end
    end else begin
      if (write_delay_cnt > 0) begin
        write_delay_cnt <= write_delay_cnt - 1;
      end else if (axi_bvalid && axi_bready) begin
        write_state <= 0;
        /* verilator lint_off UNSIGNED */
        write_delay_cnt <= ($$urandom_range(0, 99) < ${100 - delayProb}) ? 0 : $$urandom_range(1, ${maxDelayCycle});
      end
    end
  end
end

endmodule
"""
  )
}

class Axi4BurstSpliter extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new Axi4IO)
    val out = new Axi4IO
  })
  io.out.aw :<>= io.in.aw
  io.out.w :<>= io.in.w
  io.in.b :<>= io.out.b

  val burstCnt = Reg(UInt(4.W))
  when(io.in.ar.fire) {
    burstCnt := io.in.ar.bits.len
  }.elsewhen(io.in.r.fire && burstCnt =/= 0.U) {
    burstCnt := burstCnt - 1.U
  }

  val bitsReg = Reg(chiselTypeOf(io.in.ar.bits))
  when(io.in.ar.fire) {
    bitsReg := io.in.ar.bits
  }

  val sInReq :: sOutReq :: sResp :: Nil = Enum(3)
  val state = RegInit(sInReq)
  switch(state) {
    is(sInReq) { when(io.in.ar.fire) { state := sOutReq } }
    is(sOutReq) { when(io.out.ar.fire) { state := sResp } }
    is(sResp) { when(io.in.r.fire) { state := Mux(burstCnt === 0.U, sInReq, sOutReq) } }
  }

  io.in.ar.ready := state === sInReq
  io.out.ar.valid := state === sOutReq
  io.out.ar.bits := bitsReg
  io.out.ar.bits.len := 0.U
  io.in.r.valid := state === sResp && io.out.r.valid
  io.out.r.ready := state === sResp && io.in.r.ready
  io.in.r.bits := io.out.r.bits
  io.in.r.bits.id := bitsReg.id
  io.in.r.bits.last := burstCnt === 0.U
}

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
