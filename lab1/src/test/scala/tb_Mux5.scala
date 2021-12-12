import chisel3._
import chisel3.iotesters.{PeekPokeTester, chiselMainTest}

class tbMux5 (dut : Mux5) extends PeekPokeTester(dut) {
  poke(dut.io.a, 1.U)
  poke(dut.io.b, 3.U)
  poke(dut.io.c, 1.U)
  poke(dut.io.d, 3.U)
  poke(dut.io.e, 1.U)
  poke(dut.io.sel, 1.U)
  step(1)
  println("Result is:" + peek(dut.io.y).toString)
  expect(dut.io.y, 3.U)
  object tb_Mux5 extends App {
    chisel3.iotesters.Driver(() => new Mux5) {
      m => new tbMux5(m)

    }
  }
}
