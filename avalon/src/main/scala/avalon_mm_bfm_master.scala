package avalon_mm

import Chisel.{Enum, switch}
import chisel3._
import chiseltest._
import chisel3.iotesters._
import chisel3.util
import com.sun.org.apache.bcel.internal.generic.SWITCH

import javax.swing.OverlayLayout

class avalon_mm_bfm_master_if(val data_w: Int, val addr_w: Int, burst_w: Int  ) extends Bundle {
    //Write Host -> agent
    val avm_address = Output(UInt(data_w.W))
    val avm_byteenable = Output(UInt((data_w/8).W))
    //val avm_debugaccess = Output(Bool())
    val avm_read = Output(Bool())
    val avm_write = Output(Bool())
    val avm_writedata = Output(UInt(data_w.W))
    val avm_lock = Output(Bool())

    //Read Agent -> Host
    val avm_readdata = Input(UInt(data_w.W))
    val avm_response = Input(UInt(2.W))
    val avm_waitrequest  = Input(Bool())
    //Read Agent -> Host Pipelined
    val avm_readdatavalid: Bool = Input(Bool())
    val avm_writeresponsevalid: Bool = Input(Bool())
    //Write Host -> Agent Burst
    val avm_burstcount: Nothing = Output(UInt(burst_w.W))
}
class avm_mm_response_status{
    val OKAY = "b00".U
    val RESERVED = "b01".U
    val SLVERR = "b10".U
    val DECODEERROR = "b11".U
}
class avalon_mm_bfm_master_main[T <: MultiIOModule](dut :T, val avm_if: avalon_mm_bfm_master_if) extends PeekPokeTester(dut) {
    // Connect DTU with Avalon Bus Function Model
    //val avm_if = new avalon_mm_bfm_master_if

    val addr      = avm_if.avm_address
    val byteenable = avm_if.avm_byteenable
    //val debugaccess = avm_if.avm_debugaccess
    val read = avm_if.avm_read
    val write = avm_if.avm_write
    val writedata = avm_if.avm_writedata

    val readdata = avm_if.avm_readdata
    val response = avm_if.avm_response
    val waitrequest = avm_if.avm_waitrequest
    val burst_cnt = 0.U

    // DUT SIGNALS
    // Initialization
    // BFM -> DUT signals
    poke(addr,0x0000)

    // DUT -> BFM signals
    def avalon_mm_if_signal_init(addr_w: Int, data_w: Int, burst_w: Int, lock_value: Boolean) : avalon_mm_bfm_master_if = {
        val result : avalon_mm_bfm_master_if = new avalon_mm_bfm_master_if(addr_w, data_w, burst_w)
        //BFM TO DUT (Output)
        result.avm_address      := 0.U(addr_w.W)
        result.avm_byteenable   := false.B
        result.avm_read         := false.B
        result.avm_write        := false.B
        result.avm_writedata    := 0.U(data_w.W)
        result.avm_lock         := false.B
        //DUT to BFM (Input)
        result.avm_readdata := 0.U(data_w.W)
        result.avm_response := 0.U(2.W)
        result.avm_waitrequest := false.B
        result.avm_readdatavalid := false.B

        result // Return
    }

    def avalon_mm_if_response_status(response: Int): UInt = {
        object status extends avm_mm_response_status
        response match{
            case 0 => status.OKAY
            case 1 => status.SLVERR
            case 2 => status.RESERVED
            case 3 => status.DECODEERROR
        }
    }
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