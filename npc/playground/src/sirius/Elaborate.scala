package sirius

object Elaborate extends App {
  val firtoolOptions = Array(
    "-default-layer-specialization=enable",
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
  val cfg =
    CoreConfig(
      rvOpCodesPath = workspacePath / "rvdecoderdb" / "riscv-opcodes",
      // isDebug = false,
    )
  firtoolOptions.foreach(s => println(s))
  circt.stage.ChiselStage.emitSystemVerilogFile(
    new sirius.Top()(cfg, UnitConfig.default),
    args,
    firtoolOptions
  )
}
