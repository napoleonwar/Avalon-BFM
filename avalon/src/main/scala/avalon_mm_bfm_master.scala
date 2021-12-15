package avalon_mm

import Chisel.{Enum, switch}
import chisel3._
import chiseltest._
import chisel3.iotesters._
import chisel3.util
import com.sun.org.apache.bcel.internal.generic.SWITCH
import javax.swing.OverlayLayout

// Avalon MM BFM interface input/output
// Defined in Avalon Specification Chp3
class avalon_mm_bfm_master_if(val data_w: Int, val addr_w: Int, burst_w: Int  ) extends Bundle {
    // Clock and Reset????
    //    val clk = Input(UInt(1.W))
    //    val rst = Input(UInt(1.W))
    //Write OUTPUT Host -> agent
    //Read INPUT Agent -> Host
    val avm_address = Output(UInt(data_w.W))
    val avm_byteenable = Output(UInt((data_w/8).W))
    val avm_debugaccess = Output(Bool()) //for Nios II processor
    val avm_read = Output(Bool())
    val avm_readdata = Input(UInt(data_w.W))
    val avm_response = Input(UInt(2.W))
    val avm_write = Output(Bool())
    val avm_writedata = Output(UInt(data_w.W))
    val avm_lock = Output(Bool())
    val avm_waitrequest  = Input(Bool())
    // Pipelined
    val avm_readdatavalid: Bool = Input(Bool())
    val avm_writeresponsevalid: Bool = Input(Bool())
    //Write Host -> Agent Burst
    val avm_burstcount: Nothing = Output(UInt(burst_w.W))
}
//Response status, specification response signal P15
class avm_mm_response_status{
    val OKAY = "b00".U
    val RESERVED = "b01".U
    val SLVERR = "b10".U
    val DECODEERROR = "b11".U
}
//Parameters, including setup time, wait time....timing defines
//In the Verification IP User Guide
//they are constants for the rest, so defined as an object
object avalon_mm_config{
    // Port Width
    val AV_ADDRESS_W               = 32 // Address width in bits
    val AV_SYMBOL_W                = 8  // Data symbol width in bits
    val AV_READRESPONSE_W          = 8  // Read response signal width in bits
    val AV_WRITERESPONSE_W         = 8  // Write response signal width in bits
    // Parameters
    val AV_NUMSYMBOLS              = 4  // Number of symbols per word
    val AV_BURSTCOUNT_W            = 3  // Burst port width in bits
    // Port Enables (ON/OFF)
    val USE_READ                   = 1  // Use read pin on interface
    val USE_WRITE                  = 1  // Use write pin on interface
    val USE_ADDRESS                = 1  // Use address pins on interface
    val USE_BYTE_ENABLE            = 1  // Use byteenable pins on interface
    val USE_BURSTCOUNT             = 1  // Use burstcount pin on interface
    val USE_READ_DATA              = 1  // Use readdata pin on interface
    val USE_READ_DATA_VALID        = 1  // Use readdatavalid pin on interface
    val USE_WRITE_DATA             = 1  // Use writedata pin on interface
    val USE_BEGIN_TRANSFER         = 0  // Use begintransfer pin on interface
    val USE_BEGIN_BURST_TRANSFER   = 0  // Use beginbursttransfer pin on interface
    val USE_ARBITERLOCK            = 0  // Use arbiterlock pin on interface
    val USE_LOCK                   = 0  // Use lock pin on interface
    val USE_DEBUGACCESS            = 0  // Use debugaccess pin on interface
    val USE_WAIT_REQUEST           = 1  // Use waitrequest pin on interface
    val USE_TRANSACTIONID          = 0  // Use transactionid interface pin
    val USE_WRITERESPONSE          = 0  // Use writeresponse interface pins
    val USE_READRESPONSE           = 0  // Use readresponse interface pins
    val USE_CLKEN                  = 0  // Use clken interface pins
    // Port Polarity (assert signal HIGH/LOW)

    // Burst Attributes
    val AV_BURST_LINEWRAP          = 1  // Wrapping burst. Only LSB is used
    val AV_BURST_BNDR_ONLY         = 1  // Assert Addr alignment
    // Miscellaneous
    val AV_MAX_PENDING_READS       = 1  // Max number of pending reads transactions can be queued by slave
    val AV_FIX_READ_LATENCY        = 1  // USE_READ_DATA_VALID = 0. Read latency for fixed latency slave (cycles).
    val VHDL_ID                    = 0  // For VHDL only. VHDL BFM ID number
    // Timing
    val AV_FIXED_READ_WAIT_TIME    = 1  // USE_WAIT_REQUEST is 0. Fixed wait time cycles when
    val AV_FIXED_WRITE_WAIT_TIME   = 0  // USE_WAIT_REQUEST is 0
    val AV_REGISTERED_WAITREQUEST  = 0  // Waitrequest is registered at the slave. To Reg
    val AV_REGISTERINCOMINGSIGNALS = 0  // Indicate that waitrequest is come from register

    // No idea what are these
    val AV_MAX_PENDING_WRITES      = 0  // Number of pending write transactions
    val AV_CONSTANT_BURST_BEHAVIOR = 1  // Address, burstcount, transactionid and
    // avm_writeresponserequest need to be held constant
    // in burst transaction
    val MAX_BURST_SIZE            = if(USE_BURSTCOUNT != 0) 2^(AV_BURSTCOUNT_W-1) else 1
    val AV_DATA_W                 = AV_SYMBOL_W * AV_NUMSYMBOLS
    val AV_TRANSACTIONID_W        = 8
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
    def avalon_mm_write(
                         addr_value: Int,
                         data_value: Int,
                         avalon_if : avalon_mm_bfm_master_if,
                         byteenable: Int,
                         clk: Bool
                       ){
        //Before this point, some check points are set up
        //return the normalized data into IF
        //clock is not explicitly shown in chisel, so ignore the clock signal

        avalon_if.avm_writedata := normalized_data
        avalon_if.avm_byteenable := byteenable
        avalon_if.avm_write := true.B
        avalon_if.avm_address  := normalized_addr
        avalon_if := avalon_mm_if_signal_init(avalon_if.avm_address.getWidth, avalon_if.avm_writedata.getWidth,
            avalon_if.avm_burstcount, avalon_if.avm_lock.litToBoolean)
        //begintransfer
        def rising_edge(x: Bool) = x && !RegNext(x)
        //some time stamp for current operation
        //wait request
        if(waitrequest) {
            for(cycle <- 1 to max_wait_cycles ) {
                while(avalon_if.waitrequest === true.B && !rising_edge(clk)) {/*wait*/}
                if(cycle === max_wait_cycles) {
                    timeout := true.B
                    printf(p"ERROR: Waitrequest failed > Time out./n")
                }
                else {break()}
            }
        }
        else {
            for(cycle <- 1 to max_wait_write){
                while(!rising_edge(clk)) {/*waiting*/}
                //check value in range
            }
        }
        //wait for a hold time

    }
    def avalon_mm_is_readdatavalid_acvive(avalon_if: avalon_mm_bfm_master_if, config ){

    }
    def avalon_mm_is_waitrequest_acvive(avalon_if: avalon_mm_bfm_master_if, config ){

    }
    def avalon_mm_read(){

    }
    def avalon_mm_read_request
    def avalon_mm_read_response
    def avalon_mm_reset
    def avalon_mm_lock
    def avalon_mm_unlock
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