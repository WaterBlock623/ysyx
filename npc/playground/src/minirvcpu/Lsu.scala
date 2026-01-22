package minirvcpu

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup

class MemInstFetchIO(
  implicit private val cfg: CoreConfig)
    extends Bundle {
  val rAddr = Input(UInt(cfg.memoryAddrWidth.W))
  val rData = Output(UInt(cfg.xlen.W))
}

class MemLoadStoreIO(
  implicit private val cfg: CoreConfig)
    extends Bundle {
  val valid = Input(Bool())
  val rAddr = Input(UInt(cfg.memoryAddrWidth.W))
  val rData = Output(UInt(cfg.xlen.W))
  val wAddr = Input(UInt(cfg.memoryAddrWidth.W))
  val wData = Input(UInt(cfg.xlen.W))
  val wMask = Input(UInt((cfg.xlen >> 3).W))
  val wEn = Input(Bool())
}

class LsuSignals(implicit private val cfg: CoreConfig) extends Bundle {
  val loadData = Output(UInt(cfg.xlen.W))
}

class Lsu(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new Bundle {
    val memLoadStoreIO = Flipped(new MemLoadStoreIO)
    val iduIn = Flipped(new IduSignals)
    val exuIn = Flipped(new ExuSignals)
    val registerFileIn = Flipped(new RegisterFileSignals)
    val lsuOut = new LsuSignals
  })

  if (cfg.xlen != 32) {
    throw new IllegalArgumentException("Unsupported xlen")
  }

  val ctrlSig = io.iduIn.ctrlSignals.ls
  io.memLoadStoreIO.valid := ctrlSig.isLoad || ctrlSig.isStore
  io.memLoadStoreIO.wEn := ctrlSig.isStore
  val addr = io.exuIn.aluResult
  io.memLoadStoreIO.rAddr := addr
  io.memLoadStoreIO.wAddr := addr

  val rem = addr(1, 0)

  // load
  val rData = io.memLoadStoreIO.rData
  val lbu = (rData >> rem * 8.U)(7, 0)
  val lbData = Mux(ctrlSig.isUnsignedLoad, 0.U((cfg.xlen - 8).W), 
    Fill(cfg.xlen - 8, lbu(7))) ## lbu
  val lhu = (rData >> (rem(1) * 16.U))(15, 0)
  val lhData = Mux(ctrlSig.isUnsignedLoad, 0.U((cfg.xlen - 16).W), 
    Fill(cfg.xlen - 16, lhu(15))) ## lhu
  val lwData = rData(31, 0).pad(cfg.xlen)
  io.lsuOut.loadData := MuxLookup(ctrlSig.loadStoreLength, lwData)(Seq(
    LoadStoreLengthEnum.w.asUInt -> lwData,
    LoadStoreLengthEnum.h.asUInt -> lhData,
    LoadStoreLengthEnum.b.asUInt -> lbData,
    ))

  // store
  val regData = io.registerFileIn.rData(1)
  val sb = (regData(7, 0) << (rem * 8.U)).pad(cfg.xlen)
  val sh = (regData(15, 0) << (rem(1) * 16.U)).pad(cfg.xlen)
  val sw = regData(31, 0).pad(cfg.xlen)
  io.memLoadStoreIO.wData := MuxLookup(ctrlSig.loadStoreLength, sw)(Seq(
    LoadStoreLengthEnum.w.asUInt -> sw,
    LoadStoreLengthEnum.h.asUInt -> sh,
    LoadStoreLengthEnum.b.asUInt -> sb,
    ))
  val sbMask = (1.U << rem).pad(cfg.xlen)
  val shMask = (3.U << (rem & 2.U)).pad(cfg.xlen)
  val swMask = 15.U(cfg.xlen.W)
  io.memLoadStoreIO.wMask := MuxLookup(ctrlSig.loadStoreLength, swMask)(Seq(
    LoadStoreLengthEnum.w.asUInt -> swMask,
    LoadStoreLengthEnum.h.asUInt -> shMask,
    LoadStoreLengthEnum.b.asUInt -> sbMask,
    ))
}





