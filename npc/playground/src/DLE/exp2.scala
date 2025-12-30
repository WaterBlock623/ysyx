package dle.exp3

import chisel3._
import chisel3.util._

def decoderLed(in: UInt(4.W)): UInt(7.W) = {
  val out = MuxLookup(in, 0.U)(Seq(
    0.U -> "b1111110".U,
    1.U -> "b0110000".U,
    2.U -> "b1101101".U,
    3.U -> "b1111001".U,
    4.U -> "b0110011".U,
    5.U -> "b1011011".U,
    6.U -> "b1011111".U,
    7.U -> "b1110000".U,
    8.U -> "b1111111".U,
    9.U -> "b1111011".U,
    10.U -> "b1110111".U,
    11.U -> "b0011111".U,
    12.U -> "b1001110".U,
    13.U -> "b0111101".U,
    14.U -> "b1001111".U,
    15.U -> "b1000111".U
    ))
  out
}

def encoder83(in: UInt(8.W), en: Bool): UInt(3.W) = {
  val enc = Mux1H(Seq(
    in(0) -> 0.U,
    in(1) -> 1.U,
    in(2) -> 2.U,
    in(3) -> 3.U,
    in(4) -> 4.U,
    in(5) -> 5.U,
    in(6) -> 6.U,
    in(7) -> 7.U
    ))
  val out = Mux(en, enc, 0.U)
  out
}

def pEncoder83(in: UInt(8.W), en: Bool): UInt(3.W) = {
  val hotIn = VecInit.fill(8)(false.B)
  val cond = VecInit.fill(8)(false.B)

  hotIn(7) := in(7)
  cond(7) := ~in(7)
  for (i <- 6 to 0) {
    cond(i) := ~in(i) & cond(i + 1)
    hot(i) := in(i) & cond(i + 1)
  }
  
  val out = encoder83(hotIn.asUInt, en)
  out
} 

class top() extends Moudle {
  val io = IO(new Bundle {
    val sw = Input(UInt(8.W))
    val en = Input(Bool())
    val led = Output(UInt(3.W))
    val hasOne = Output(Bool())
    val led7 = Output(UInt(7.W))
  })
  io.hasOne := io.sw.orR
  io.led := pEncoder(io.sw, io.en)
  io.led7 := decoderLed(false.B ## io.led)
}

