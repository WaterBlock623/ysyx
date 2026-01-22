package minirvcpu

import cpuutil.BundleGenerator

object GenCtrlSignals extends App {
  val rootStr = System.getProperty("project.root")
  val workspacePath = os.Path(rootStr)
  val cfg =
    CoreConfig(rvOpcodesPath = workspacePath / "rvdecoderdb" / "riscv-opcodes")
  val collector = InstDecodeCollector()(cfg)
  val gen = new BundleGenerator("minirvcpu", "CtrlSignals", collector.allFields)
  gen.generate(args(0) + "/CtrlSignals.scala")
}
