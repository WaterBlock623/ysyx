import chisel3._
import chisel3.util._
// LTL (线性时序逻辑) 用于定义形式化属性
import chisel3.ltl._
// 核心：导入大写的 Formal 对象
import chisel3.simulator.Formal
import org.scalatest.flatspec.AnyFlatSpec

class Sub extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(4.W))
    val b = Input(UInt(4.W))
    val c = Output(UInt(4.W))
  })

  // 当 io.a === 2.U 时，c = a + ~b + 0 = a - b - 1 (不等于 ref)
  io.c := io.a + ~io.b + Mux(io.a === 2.U, 0.U, 1.U)

  val ref = io.a - io.b

  // Chisel 7+ 推荐使用 AssertProperty
  // 它会被自动映射到形式化后端的 Assert 指令
  AssertProperty(io.c === ref)
}

class FormalTest extends AnyFlatSpec {
  "Sub" should "pass formal verification" in {
    // 使用新的 Formal.verify 接口
    // 默认后端通常是 svsim + yosys-smtbmc
    Formal.verify(new Sub(), Seq(Formal.BoundedCheck(1)))
  }
}
