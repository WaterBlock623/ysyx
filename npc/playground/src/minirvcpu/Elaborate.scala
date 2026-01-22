package minirvcpu

object Elaborate extends App {
  val firtoolOptions = Array(
    "--lowering-options=" + List(
      // make yosys happy
      // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).reduce(_ + "," + _)
  )
  val rootStr = System.getProperty("project.root")
  val workspacePath = os.Path(rootStr)
  val cfg = CoreConfig(rvOpcodesPath = workspacePath / "rvdecoderdb" / "riscv-opcodes")
  circt.stage.ChiselStage.emitSystemVerilogFile(new minirvcpu.Top()(cfg), args, firtoolOpts = Array("--split-verilog=false"))
}
