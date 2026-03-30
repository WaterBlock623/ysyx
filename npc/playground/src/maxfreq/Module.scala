package maxfreq

import chisel3._

class RegFile extends Module {
  val inRaw = IO(new Bundle {
    val rAddr = Input(Vec(2, UInt(4.W)))
    val wEn   = Input(Bool())
    val wAddr = Input(UInt(4.W))
    val wData = Input(UInt(32.W))
  })
  val outRaw = IO(new Bundle {
    val rData = Output(Vec(2, UInt(32.W)))
  })

  val in = RegNext(inRaw) 
  val out = Wire(chiselTypeOf(outRaw))
  outRaw := RegNext(out)

  val regFile = Reg(Vec(16, UInt(32.W)))
  when(in.wEn) {
    regFile(in.wAddr) := in.wData
  }
  regFile(0) := 0.U
  out.rData(0) := regFile(in.rAddr(0))
  out.rData(1) := regFile(in.rAddr(1))
}

class Adder extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(32.W))
    val b = Input(UInt(32.W))
    val out = Output(UInt(32.W))
  })
  io.out := RegNext(RegNext(io.a) + RegNext(io.b))
}
