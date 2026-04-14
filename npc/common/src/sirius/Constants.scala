package sirius

import chisel3._

object ExtTypeEnum extends ChiselEnum {
  val I, Zicsr, Zifencei, M = Value
}

object InstTypeEnum extends ChiselEnum {
  val R, I, S, B, U, J, Zicsr = Value
}

object AluInSelEnum extends ChiselEnum {
  val imm, rs, pc, csr = Value
}

object AluOpEnum extends ChiselEnum {
  val add, sub, 
    and, or, xor,
    eql, neq, lt, ltu, ge, geu, 
    sll, srl, sra,
    direct1, clear = Value
}

object ExuOutSelEnum extends ChiselEnum {
  val aluBase = Value
}

object JumpTargetSelEnum extends ChiselEnum {
  val alu, pcPlusImm, mtvec, mepc = Value
}

object WriteBackSelEnum extends ChiselEnum {
  val alu, imm, staticNextPc, lsu, csr = Value
}

object LoadStoreTypeEnum extends ChiselEnum {
  val signedLoad, unsignedLoad, store = Value
}

object LoadStoreLengthEnum extends ChiselEnum {
  val b, h, w = Value
}

object CsrEnum extends ChiselEnum {
  val mcycle, mcycleh, mvendorid, marchid = Value
}

object CsrWOpCode extends ChiselEnum {
  val write, set, clear = Value
}

object McauseEnum {
  val LoadAddressMisaligned: BigInt = 4
  val LoadAccessFault: BigInt = 5
  val StoreOrAmoAddressMisaligned: BigInt = 6
  val StoreOrAmoAccessFault: BigInt = 7
  val EnvironmentCallFromM: BigInt = 11
}
