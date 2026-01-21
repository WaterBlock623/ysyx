package minirvcpu

import chisel3._
import chisel3.util._


class Top(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val ifuOut = new IfuSignals
    val lsuIn = Flipped(new LsuSignals)
  })

  val pcRegister = Module(new PcRegister)
  val registerFile = Module(new RegisterFile)
  val ifu = Module(new Ifu)
  val idu = Module(new Idu)
  val exu = Module(new Exu)
  val wbu = Module(new Wbu)

  val pcRegisterOut = pcRegister.io.pcRegisterOut
  val registerFileOut = registerFile.io.registerFileOut
  val ifuOut = ifu.io.ifuOut
  val iduOut = idu.io.iduOut
  val exuOut = exu.io.exuOut
  val wbuOut = wbu.io.wbuOut
  
  io.ifuOut := ifuOut
  pcRegister.io.wbuIn := wbuOut
  registerFile.io.wbuIn := wbuOut
  ifu.io.pcRegisterIn := pcRegisterOut
  ifu.io.lsuIn := io.lsuIn
  idu.io.ifuIn := ifuOut
  exu.io.iduIn := iduOut
  exu.io.regFileIn := registerFileOut
  wbu.io.exuIn := exuOut
  wbu.io.iduIn := iduOut
}

