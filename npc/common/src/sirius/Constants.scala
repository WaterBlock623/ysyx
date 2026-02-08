package sirius

import chisel3._

object ExtTypeEnum extends ChiselEnum {
  val I, M = Value
}

object InstTypeEnum extends ChiselEnum {
  val R, I, S, B, U, J = Value
}

object AluInSelEnum extends ChiselEnum {
  val imm, rs2 = Value
}

object AluOpEnum extends ChiselEnum {
  val add, sub = Value
}

object ExuOutSelEnum extends ChiselEnum {
  val aluBase = Value
}

object JumpTargetSelEnum extends ChiselEnum {
  val imm, alu = Value
}

object WriteBackSelEnum extends ChiselEnum {
  val alu, imm, staticNextPc, lsu = Value
}

object LoadStoreTypeEnum extends ChiselEnum {
  val signedLoad, unsignedLoad, store = Value
}

object LoadStoreLengthEnum extends ChiselEnum {
  val b, h, w = Value
}
