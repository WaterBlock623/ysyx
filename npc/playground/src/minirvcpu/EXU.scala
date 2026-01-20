package minirvcpu

import chisel3._

class AluBase(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val aluBaseOp = Input(UInt(4.W))
    val src1 = Input(UInt(cfg.xlen.W))
    val src2 = Input(UInt(cfg.xlen.W))
    val out = Output(UInt(cfg.xlen.W))
  })


}
