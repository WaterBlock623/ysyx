package minirvcpu

import chisel3._

class MemSignals(implicit val cfg: CoreConfig) 
  extends MemSignalsTemplate(rPortNum = 2,
                             wPortNum = 1,
                             addrWidth = cfg.memoryAddrWidth,
                             dataWidth = cfg.xlen)

class LSU(implicit private val cfg: CoreConfig) extends Module {
  val io = IO(new MemSignals)
}
