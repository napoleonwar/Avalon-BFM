import avalon_mm._
import chisel3._
import chisel3.iotesters.PeekPokeTester
import org.scalatest._
import avalon_mm.request_t._
import avalon_mm.response_status._


class avalon_mm_bfm_master_test(dut: avalon_mm_bfm_master_if)extends PeekPokeTester(dut) {
  poke(dut.io.avm_read, true.B)
  poke(dut.io.avm_byteenable, 0x1111)
  poke(dut.io.avm_waitrequest, true.B)
  poke(dut.io.avm_address, 0x123456)
  step(4)
  poke(dut.io.avm_read, true.B)
  poke(dut.io.avm_byteenable, 0x1111)
  poke(dut.io.avm_waitrequest, true.B)
  poke(dut.io.avm_address, 0x123456)
  step(4)
  poke(dut.io.avm_read, true.B)
  poke(dut.io.avm_byteenable, 0x1111)
  poke(dut.io.avm_waitrequest, false.B)
  poke(dut.io.avm_address, 0x123456)
  poke(dut.io.avm_readdata,0x22334455)
  poke(dut.io.avm_response, 0)
  step(6)
  poke(dut.io.avm_read, false.B)
  poke(dut.io.avm_byteenable, 0x0000)
  poke(dut.io.avm_waitrequest, false.B)
  poke(dut.io.avm_address, 0)
  poke(dut.io.avm_readdata,0)
  poke(dut.io.avm_response, 0)
  step(6)
  poke(dut.io.avm_write, true.B)
  poke(dut.io.avm_byteenable, 0x1111)
  poke(dut.io.avm_waitrequest, true.B)
  poke(dut.io.avm_address, 0x123456)
  poke(dut.io.avm_writedata,0x223344)
  step(5)
  poke(dut.io.avm_write, true.B)
  poke(dut.io.avm_byteenable, 0x1111)
  poke(dut.io.avm_waitrequest, false.B)
  poke(dut.io.avm_address, 0x123456)
  poke(dut.io.avm_writedata,0x223344)
  step(5)
  poke(dut.io.avm_write, false.B)
  poke(dut.io.avm_byteenable, 0x1111)
  poke(dut.io.avm_address, 0x123456)
  poke(dut.io.avm_writedata,0x223344)
  


  object valon_mm_bfm_master_test extends App {
    chisel3.iotesters.Driver(() => new avalon_mm_bfm_master_main(dut)) {
      m => new avalon_mm_bfm_master_test(m)
    }
  }
}
