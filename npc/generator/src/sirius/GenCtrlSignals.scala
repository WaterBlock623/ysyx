package sirius

import cpuutil.BundleGenerator

object GenCtrlSignals extends App {
  val rootStr = System.getProperty("project.root")
  val workspacePath = os.Path(rootStr)
  val rvOpCodesPath = workspacePath / "rvdecoderdb" / "riscv-opcodes"
  println(rvOpCodesPath)
  val cfg = CoreConfig(xlen = 1, rvOpCodesPath = rvOpCodesPath)
  val collector = InstDecodeCollector()(cfg)
  val gen = new BundleGenerator("sirius", "CtrlSignals", collector.allFields)
  gen.generate(args(0) + "/CtrlSignals.scala")
}
