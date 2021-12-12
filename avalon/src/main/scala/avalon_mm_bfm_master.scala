package avalon_mm

import chisel3._
import chiseltest._
import chisel3.iotesters._
import chisel3.util

class avalon_mm_bfm_master_if(val data_w: Int, val addr_w: Int ) extends Bundle {
    //Write Host -> agent
    val avm_address = Output(UInt(data_w.W))
    val avm_byteenable = Output(UInt((data_w/8).W))
    val avm_debugaccess = Output(Bool())
    val avm_read = Output(Bool())
    val avm_write = Output(Bool())
    val avm_writedata = Output(UInt(data_w.W))

    //Read Agent -> Host
    val avm_readdata = Input(UInt(data_w.W))
    val avm_response = Input(UInt(2.W))
    val avm_waitrequest  = Input(Bool())
}
class avalon_mm_bfm_master_if_pipeline(val data_w: Int, val addr_w: Int ) extends Bundle{
    val avm_if = new avalon_mm_bfm_master_if(data_w, addr_w)
    // Read Agent -> Host
    val avm_readdatavalid: Bool = Input(Bool())
    val avm_writeresponsevalid: Bool = Input(Bool())
}
class avalon_mm_bfm_master_if_burst(val data_w: Int, val addr_w: Int, val burst_w: Int ) extends Bundle {
    val avm_if = new avalon_mm_bfm_master_if(data_w, addr_w)
    // Write Host -> Agent
    val avm_burstcount: Nothing = Output(UInt(burst_w.W))
}
class avalon_mm_bfm_master_main[T <: MultiIOModule](dut :T, val avm_if: avalon_mm_bfm_master_if) extends PeekPokeTester(dut) {
    // Connect DTU with Avalon Bus Function Model
    //val avm_if = new avalon_mm_bfm_master_if
    // DUT SIGNALS
    val addr      = dut.avm_address
    val byteenable = dut.avm_byteenable
    val debugaccess = dut.avm_debugaccess
    val read = dut.avm_read
    val write = dut.avm_write
    val writedata = dut.avm_writedata

    val readdata = dut.avm_readata
    val response = dut.avm_response
    val waitrequest = dut.avm_waitrequest
    val burst_cnt = 0.U

    // Initialization
    // BFM -> DUT signals
    poke(addr,0x0000)
    peek()
    // DUT -> BFM signals

    def avalon_mm_wr(waitrequest: Boolean, t: Long) : Unit = {
        val write = peek(avm_if.avm_write) > 0
        if(!waitrequest && write) {
            if(avm_if.avm_address.isEmpty) {
                addr = peek(avm_if.avm_address).U
                burst_cnt = peek(avm_if.avm_burstcount) - 1
                printf("Receive write address: %d, Burst count number: %d /n", addr, burst_cnt )
            }
        }
    }
    
}