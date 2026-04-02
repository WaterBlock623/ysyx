package sirius

object Elaborate extends App {
  val argMap = args
    .sliding(2, 2)
    .collect {
      case Array(key, value) if key.startsWith("--") =>
        key.stripPrefix("--") -> value
    }
    .toMap

  val firtoolOptions = Array(
    "-default-layer-specialization=enable",
    "--verification-flavor=immediate",
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
      isDebug = argMap.getOrElse("debug", "true").toBoolean,
      ysyxsoc = argMap.getOrElse("ysyxsoc", "false").toBoolean,
      perf = argMap.getOrElse("perf", "false").toBoolean,
      pcInit =
        BigInt(argMap.getOrElse("pc-init", "0x30000000").stripPrefix("0x"), 16)
    )
  firtoolOptions.foreach(s => println(s))
  circt.stage.ChiselStage.emitSystemVerilogFile(
    new sirius.Top()(cfg, UnitConfig.default),
    if (argMap.contains("target-dir")) {
      Array("--target-dir", argMap("target-dir"))
    } else { Array("--help") },
    firtoolOptions
  )
}
