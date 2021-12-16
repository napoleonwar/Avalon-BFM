package avalon_mm

import Chisel.{Enum, Queue, switch}
import avalon_mm.avalon_mm_config.{AV_ADDRESS_W, AV_BURSTCOUNT_W, AV_DATA_W, AV_NUMSYMBOLS, AV_READRESPONSE_W, AV_TRANSACTIONID_W, MAX_BURST_SIZE}
import chisel3._
import chisel3.experimental.ChiselEnum
import chiseltest._
import chisel3.iotesters._

import scala.collection.mutable.Queue
import chisel3.util
import com.sun.org.apache.bcel.internal.generic.{NEW, SWITCH}

import javax.swing.OverlayLayout
import scala.collection.mutable

// Avalon MM BFM interface input/output
// Defined in Avalon Specification Chp3
class avalon_mm_bfm_master_if(val data_w: Int, val addr_w: Int, burst_w: Int) extends Bundle {
    // Clock and Reset????
    val clk = Input(Clock())
    val rst = Input(Reset())
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

    // There are some pins not shown in the specification but in IP guide
    // Need to be added later
    // avm_transactionid avm_readid avm_writeid
}

//Parameters, including setup time, wait time....timing defines
//In the Verification IP User Guide
//they are constants for the rest, so defined as an object
object avalon_mm_config extends Bundle {
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

    // To do: No idea what are these
    val AV_MAX_PENDING_WRITES      = 0  // Number of pending write transactions
    val AV_CONSTANT_BURST_BEHAVIOR = 1  // Address, burstcount, transactionid and
    // avm_writeresponserequest need to be held constant
    // in burst transaction
    val MAX_BURST_SIZE            = if(USE_BURSTCOUNT != 0) 2^(AV_BURSTCOUNT_W-1) else 1
    val AV_DATA_W                 = AV_SYMBOL_W * AV_NUMSYMBOLS
    val AV_TRANSACTIONID_W        = 8


}
// ********************* Descriptor *****************
// Include 2 Descriptors and 3 queues
// Write Transaction: To slave, with Burst enable
// Command  Descriptor Structure, receive Transaction level commmand from BEM API
class MasterCommand_t {
    val request = new response_t
    val address = UInt((AV_ADDRESS_W).W)      // start address
    val burstcount = UInt((AV_BURSTCOUNT_W).W) // the burst data length
    val writedata = Vec(MAX_BURST_SIZE, AV_DATA_W)  // data point to agent
    val byte_enbale = Vec(MAX_BURST_SIZE, AV_NUMSYMBOLS) // byte choose
    val idle = Vec(MAX_BURST_SIZE, 32) // ?????what is this?????
    val init_latency = 0
    val seq_count = 0
    val burst_size = MAX_BURST_SIZE
    val arbiterlock = false
    val lock = 0
    val debugacess = 0
    val transaction_id = UInt((AV_TRANSACTIONID_W).W)
    val write_response_request = false
}
// Response descriptor: Should give proper response to both Write and Read
class MasterResponse_t {
    val request = new response_t
    val address = UInt((AV_ADDRESS_W).W)      // start address
    val burstcount = UInt((AV_BURSTCOUNT_W).W) // the burst data length
    val data = Vec(MAX_BURST_SIZE, AV_DATA_W)  // data point to agent
    val byte_enbale = Vec(MAX_BURST_SIZE, AV_NUMSYMBOLS-1) // byte choose
    val wait_latency = Vec(MAX_BURST_SIZE, 32) // a vector has INT members
    val read_latency = Vec(MAX_BURST_SIZE, 32)
    val write_latency = 0
    val seq_count = 0
    val burst_size = MAX_BURST_SIZE
    val read_id = UInt((AV_TRANSACTIONID_W).W)
    val write_id = UInt((AV_TRANSACTIONID_W).W)
    val read_response = Vec(MAX_BURST_SIZE, AV_READRESPONSE_W)
    val write_response = new response_status
    val write_response_request = false
}
// Issued command queue
class IssuedCommand_t {
    val command = new MasterCommand_t
    val time_stamp = Vec(MAX_BURST_SIZE, 32)
    val wait_time = Vec(MAX_BURST_SIZE, 32)
}
// Response status enum
class response_t extends ChiselEnum {
    val REQ_IDLE, REQ_READ, REQ_WRITE = Value
}
//Response status, specification response signal P15
class response_status extends ChiselEnum{
    val OKAY, RESERVED, SLVERR, DECODEERROR = Value
}

//****************************************

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
    // Set up some globle variables
    var command_issued_counter      = 0 
    var command_completed_counter   = 0 
    var command_outstanding_counter = 0 
    var command_sequence_counter    = 1 

    var response_timeout    = 100  // disabled when 0
    var command_timeout     = 100  // disabled when 0
    var max_command_queue_size  = 256 
    var min_command_queue_size  = 2 
    var temp_write_latency  = 0 
    var response_time_stamp = 0 
    var temp_read_latency   = 0 
    var read_response_burst_counter = 0 
    var response_time_stamp_queue = mutable.Queue[Int]() 
    var start_construct_complete_write_response = 0 
    var start_construct_complete_read_response = 0 
    // Command Descriptor -> Command Queue -> Driver
    //                                           |
    //                                        Fabric BUs
    //                                           |
    // Response Descriptor <- Response Queue <- Receiver
    // Build up necessary types for Command/Response queue
    // Instantiate Queues and Descriptors
    val pending_command_queue = mutable.Queue[MasterCommand_t]()
    val pending_read_response_queue = mutable.Queue[MasterCommand_t]()
    val pending_write_response_queue = mutable.Queue[MasterCommand_t]()

    val issued_write_command_queue = mutable.Queue[IssuedCommand_t]()
    val issued_read_command_queue = mutable.Queue[IssuedCommand_t]()
    // Before signals go to the Bus,
    // DUT SIGNALS
    // Initialization
    // BFM -> DUT signals
    poke(addr,0x0000)

    // DUT -> BFM signals
    // DUT <-> BFM functions
    // From here, 50+ BFM functions illustrated in Verification IP Guide
    // will be implemented, still have some questions about DUT-IF connection

    def get_version() = "Avalon Interface in Chisel"

    def set_response_timeout(cycles : Int): Unit = { //Unit = void
        if(cycles != 0){
            response_timeout = cycles
            printf(p"Response timeout is $response_timeout.\n")
            printf(p"Input Cycles are $cycles")
        }
        else printf(p"Cycles are $cycles, disable timeout.\n")
    }
    // Waiting for waitrequest and before timeout happens
    def set_command_timeout(cycles : Int): Unit = { //Unit = void
        if(cycles != 0){
            command_timeout = cycles
            printf(p"Response timeout is $command_timeout.\n")
            printf(p"Input Cycles are $cycles")
        }
        else printf(p"Cycles are $cycles, disable timeout.\n")
    }
    // All queued transtractions are finished
    def all_transactions_complete(): Unit = {}
    //Queries the command queue to determine number of pending commands waiting to be
    //driven out as Avalon requests.
    def get_command_pending_queue_size(): Int = {}
    //Queries the issued command queue to determine the number of commands that have been
    //driven to the system interconnect fabric, but not completed.
    def get_command_issued_queue_size(): Int = { val length = issued_read_command_queue.length}


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