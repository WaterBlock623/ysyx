package rvdecodedbtest

import org.chipsalliance.rvdecoderdb
import os.Path

object Test extends App {
  val insts = rvdecoderdb.instructions(Path("/home/waterblock/ysyx-workbench/npc/rvdecoderdb/riscv-opcodes/"))
  val inst = insts.filter(_.name.contains("fence"))
  inst.foreach{println(_)}
}
