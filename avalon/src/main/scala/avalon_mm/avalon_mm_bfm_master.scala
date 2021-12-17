package avalon_mm

import avalon_mm.avalon_mm_config.{AV_ADDRESS_W, AV_BURSTCOUNT_W, AV_DATA_W, AV_NUMSYMBOLS, AV_TRANSACTIONID_W, MAX_BURST_SIZE, USE_BURSTCOUNT, USE_READ_DATA_VALID, USE_WRITERESPONSE}
import avalon_mm.request_t.{REQ_IDLE, REQ_READ, REQ_WRITE}
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.iotesters._
import chisel3.util.Enum
import chiseltest._

import scala.collection.immutable.Nil
import scala.collection.mutable

class avalon_mm_bfm_master_if(val data_w: Int, val addr_w: Int, burst_w: Int) extends Bundle {
    // Clock and Reset????
    val clk              = Input(Clock())
    val rst              = Input(Reset())
    //Write OUTPUT Host -> agent
    //Read INPUT Agent -> Host
    val avm_address     = Output(UInt(data_w.W))
    val avm_byteenable  = Output(UInt((data_w / 8).W))
    val avm_debugaccess = Output(Bool()) //for Nios II processor
    val avm_read        = Output(Bool())
    val avm_readdata    = Input(UInt(data_w.W))
    val avm_response    = Input(UInt(2.W))
    val avm_write       = Output(Bool())
    val avm_writedata   = Output(UInt(data_w.W))
    val avm_lock        = Output(Bool())
    val avm_waitrequest = Input(Bool())
    // Pipelined
    val avm_readdatavalid       = Input(Bool())
    val avm_writeresponsevalid  = Input(Bool())
    //Write Host -> Agent Burst
    val avm_burstcount  = Output(UInt(burst_w.W))

    // There are some pins not shown in the specification but in IP guide
    // Need to be added later
    // avm_transactionid avm_readid avm_writeid
}
//Parameters, including setup time, wait time....timing defines
//In the Verification IP User Guide
//they are constants for the rest, so defined as an object
object avalon_mm_config extends Bundle {
    // Port Width
    val AV_ADDRESS_W        = 32 // Address width in bits
    val AV_SYMBOL_W         = 8 // Data symbol width in bits
    val AV_READRESPONSE_W   = 8 // Read response signal width in bits
    val AV_WRITERESPONSE_W  = 8 // Write response signal width in bits
    // Parameters
    val AV_NUMSYMBOLS       = 4 // Number of symbols per word
    val AV_BURSTCOUNT_W     = 3 // Burst port width in bits
    // Port Enables (ON/OFF)
    val USE_READ            = 1 // Use read pin on interface
    val USE_WRITE           = 1 // Use write pin on interface
    val USE_ADDRESS         = 1 // Use address pins on interface
    val USE_BYTE_ENABLE     = 1 // Use byteenable pins on interface
    val USE_BURSTCOUNT      = 1 // Use burstcount pin on interface
    val USE_READ_DATA       = 1 // Use readdata pin on interface
    val USE_READ_DATA_VALID = 1 // Use readdatavalid pin on interface
    val USE_WRITE_DATA      = 1 // Use writedata pin on interface
    val USE_BEGIN_TRANSFER  = 0 // Use begintransfer pin on interface
    val USE_BEGIN_BURST_TRANSFER = 0 // Use beginbursttransfer pin on interface
    val USE_ARBITERLOCK     = 0 // Use arbiterlock pin on interface
    val USE_LOCK            = 0 // Use lock pin on interface
    val USE_DEBUGACCESS     = 0 // Use debugaccess pin on interface
    val USE_WAIT_REQUEST    = 1 // Use waitrequest pin on interface
    val USE_TRANSACTIONID   = 0 // Use transactionid interface pin
    val USE_WRITERESPONSE   = 0 // Use writeresponse interface pins
    val USE_READRESPONSE    = 0 // Use readresponse interface pins
    val USE_CLKEN           = 0 // Use clken interface pins
    // Port Polarity (assert signal HIGH/LOW)

    // Burst Attributes
    val AV_BURST_LINEWRAP   = 1 // Wrapping burst. Only LSB is used
    val AV_BURST_BNDR_ONLY  = 1 // Assert Addr alignment
    // Miscellaneous
    val AV_MAX_PENDING_READS    = 1 // Max number of pending reads transactions can be queued by slave
    val AV_FIX_READ_LATENCY     = 1 // USE_READ_DATA_VALID = 0. Read latency for fixed latency slave (cycles).
    val VHDL_ID                 = 0 // For VHDL only. VHDL BFM ID number
    // Timing
    val AV_FIXED_READ_WAIT_TIME     = 1 // USE_WAIT_REQUEST is 0. Fixed wait time cycles when
    val AV_FIXED_WRITE_WAIT_TIME    = 0 // USE_WAIT_REQUEST is 0
    val AV_REGISTERED_WAITREQUEST   = 0 // Waitrequest is registered at the slave. To Reg
    val AV_REGISTERINCOMINGSIGNALS  = 0 // Indicate that waitrequest is come from register

    val AV_MAX_PENDING_WRITES       = 0 // Number of pending write transactions
    val AV_CONSTANT_BURST_BEHAVIOR  = 1 // Address, burstcount, transactionid and avm_writeresponserequest
    val MAX_BURST_SIZE  = if (USE_BURSTCOUNT != 0) 2 ^ (AV_BURSTCOUNT_W - 1) else 1
    val AV_DATA_W       = AV_SYMBOL_W * AV_NUMSYMBOLS
    val AV_TRANSACTIONID_W = 8
}

// ********************* Descriptor *****************
// Include 2 Descriptors and 3 queues
// Write Transaction: To slave, with Burst enable
// Command  Descriptor Structure, receive Transaction level commmand from BFM API
class MasterCommand_t {
    var request             = REQ_IDLE
    var address             = UInt((AV_ADDRESS_W).W) // start address
    var burstcount          = UInt((AV_BURSTCOUNT_W).W) // the burst data length
    var writedata           = Array(MAX_BURST_SIZE, AV_DATA_W)  // data point to agent
    var byte_enable         = Array(MAX_BURST_SIZE, AV_NUMSYMBOLS) // byte choose
    var idle: Array[Int]    = Array(MAX_BURST_SIZE, 32) // ?????what is this?????
    var init_latency        = 0
    var seq_count           = 0
    var burst_size          = MAX_BURST_SIZE
    var arbiterlock         = false
    var lock                = false
    var debugacess          = false
    val transaction_id      = UInt((AV_TRANSACTIONID_W).W)
    var write_response_valid = false
}
// Response descriptor: Should give proper response to both Write and Read
class MasterResponse_t {
    var request = REQ_IDLE
    var address = UInt((AV_ADDRESS_W).W) // start address
    var burstcount = UInt((AV_BURSTCOUNT_W).W) // the burst data length
    var data = Array(MAX_BURST_SIZE, AV_DATA_W) // data point to agent
    var byte_enable = Array(MAX_BURST_SIZE, AV_NUMSYMBOLS - 1) // byte choose
    var wait_latency = Array(MAX_BURST_SIZE, 32) // a vector has INT members
    val read_latency        = Array(MAX_BURST_SIZE, 32)
    val write_latency       = Array(MAX_BURST_SIZE, 32)
    var seq_count = 0
    var burst_size = MAX_BURST_SIZE
    val read_id             = UInt((AV_TRANSACTIONID_W).W)
    val write_id            = UInt((AV_TRANSACTIONID_W).W)
    val read_response       = Array(MAX_BURST_SIZE, 2)
    var write_response       = response_status
    var write_response_valid = false
}
// Issued command queue
class IssuedCommand_t {
    var command = new MasterCommand_t
    val time_stamp  = Array(MAX_BURST_SIZE, 32)
    val wait_time   = Array(MAX_BURST_SIZE, 32)
}
// Response status enum
object  request_t extends ChiselEnum {
    val REQ_IDLE, REQ_READ, REQ_WRITE = Value
}
//Response status, specification response signal P15
object response_status extends ChiselEnum{
    val OKAY, RESERVED, SLVERR, DECODEERROR = Value
}
// Did not use
//class AvalonTransactionId_t {
//    type tran = UInt(AV_TRANSACTIONID_W.W)
//    def Tran_Id = UInt(AV_TRANSACTIONID_W.W)
//}
//class AvalonReadResponse_t {
//    val Rd_Res = Vec(MAX_BURST_SIZE, AV_READRESPONSE_W)
//}
//class AvalonResponseStatus_t {
//    val Res_Status = Vec(MAX_BURST_SIZE, 2)
//}

//****************************************

class avalon_mm_bfm_master_main[T <: MultiIOModule](dut :T, val avm_if: avalon_mm_bfm_master_if) extends PeekPokeTester(dut) {
    // Connect DUT with Avalon Bus Function Model
    val addr        = avm_if.avm_address
    val byteenable  = avm_if.avm_byteenable
    val debugaccess = avm_if.avm_debugaccess
    val read        = avm_if.avm_read
    val write       = avm_if.avm_write
    val writedata   = avm_if.avm_writedata

    val readdata    = avm_if.avm_readdata
    val response    = avm_if.avm_response
    val waitrequest = avm_if.avm_waitrequest
    val burst_cnt = 0
    //*********************** Set up Registers about timing ******//
    val clock_counter = RegInit(0.U)
    //********************** Set up some globle variables*********//
    var command_issued_counter      = 0 
    var command_completed_counter   = 0 
    var command_outstanding_counter = 0 
    var command_sequence_counter    = 1

    var wait_time_stamp     = 0
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
    //************************* Queues *********************************//
    // Build up necessary types for Command/Response queue
    // Instantiate Queues and Descriptors
    val pending_command_queue           = mutable.Queue[MasterCommand_t]()
    val pending_read_response_queue     = mutable.Queue[MasterResponse_t]()
    val pending_write_response_queue    = mutable.Queue[MasterResponse_t]()

    val issued_command_queue            = new IssuedCommand_t
    val issued_write_command_queue      = mutable.Queue[IssuedCommand_t]()
    val issued_read_command_queue       = mutable.Queue[IssuedCommand_t]()

    var completed_command               = new IssuedCommand_t
    val completed_read_command          = new IssuedCommand_t
    val completed_write_command         = new IssuedCommand_t
    val completed_read_response         = new MasterResponse_t
    val completed_write_response        = new MasterResponse_t
    // Before signals go to the Bus,
    // DUT SIGNALS
    // global signal command_temp, store the new generated seq, configure by Set_() function
    var command_temp    = new MasterCommand_t
    var master_response = new MasterResponse_t
    var command_cur     = new MasterCommand_t
    ///********************* API Functions **********************///
    // From here, 50+ BFM functions illustrated in Verification IP Guide
    // will be implemented, still have some questions about DUT-IF connection

    // All queued transtractions are finished
    def all_transactions_complete(): Boolean = {get_command_issued_queue_size() == 0 && get_command_pending_queue_size() == 0}

    // **************** From 5.4.8, AVALON MM MASTER API
    // Queries the issued command queue to determine the number of commands that have been
    // driven to the system interconnect fabric, but not completed.
    def get_command_issued_queue_size(): Int = issued_read_command_queue.length + issued_write_command_queue.length
    // Queries the command queue to determine number of pending commands waiting to be
    // driven out as Avalon requests.
    def get_command_pending_queue_size(): Int = pending_command_queue.length
    // Queries the read response queue to determine number of response descriptors currently
    // stored in the BFM
    def get_read_response_queue_size(): Int = pending_read_response_queue.length

    // ********************** Transaction Monitor ****************************
    // Returns the transaction address in the response descriptor that has been
    // removed from the response queue
    def get_response_address() : UInt = master_response.address

    def get_response_byte_enable(index: Int) : Int = master_response.byte_enable(index)

    def get_response_burst_size() : Int = master_response.burst_size

    def get_response_data(index: Int) : Int = master_response.data(index)
    // Returns the transaction read latency in the response descriptor
    def get_response_latency(index: Int) : Int = {
        if( master_response.request == REQ_READ)
            master_response.read_latency(index)
        else if(master_response.request == REQ_WRITE)
            master_response.write_latency(index)
    }

    def get_response_queue_size(): Int = pending_read_response_queue.length

    def get_response_read_response(index: Int): Int = master_response.read_response(index)

    def get_response_request():  request_t.Type = master_response.request
    // 5.4.20
    def get_response_wait_time(index: Int) : Int = master_response.wait_latency(index)

    def get_write_response_status(): response_status.Type = {
        if(master_response.request == REQ_WRITE){
            if(USE_WRITERESPONSE == 1 && master_response.write_response_valid)
                master_response.write_response
            else {
                printf(p"No Write Response")
            }
        }
    }
    def get_write_response_queue_size(): Int = pending_read_response_queue.length + pending_write_response_queue.length
    def get_version() = "Avalon Interface in Chisel"
    // Initializes the Avalon-MM master interface. 1. init descriptors 2. init queues 3. setup interface signals
    def init() : Unit = {
        printf(p"Start Initialization\n")
        avm_if.avm_address := 0.U
        avm_if.avm_byteenable := 0.U
        avm_if.avm_debugaccess := false.B
        avm_if.avm_read := false.B
        avm_if.avm_write := false.B
        avm_if.avm_writedata := 0.U
        avm_if.avm_lock := false.B
        avm_if.avm_burstcount := 0.U

    }
    // Removes the oldest response descriptor from the response queue
    def pop_response(): Unit = {
        // Get the index of the first element
        val read_seq_count = pending_read_response_queue.head.seq_count
        val write_seq_count = pending_write_response_queue.head.seq_count

        // If read command first comes to the queue, read index < write index, or no write
        // in the queue
        if(pending_read_response_queue.nonEmpty && ((read_seq_count < write_seq_count) || write_seq_count ==0))
            master_response = pending_read_response_queue.dequeue()
        else if(pending_write_response_queue.nonEmpty)
            master_response = pending_write_response_queue.dequeue()
        else printf(p"POP elements failed\n")
    }
    // Inserts the fully populated transaction descriptor onto the pending transaction command
    //  queue
    def push_command(): Unit = {
        command_temp.seq_count = command_sequence_counter //update the sequence index
        command_sequence_counter += 1
        pending_command_queue += command_temp // push to the command queue

        command_temp.request match {
            case  REQ_READ => printf(p"Read command $command_temp.address \n")
            case  REQ_WRITE => printf(p"Write command \n")
            case _ => printf(p"Error: Invalid request \n")
        }
    }

    // Set up the upcoming sequence: Set functions
    // Sets the transaction address in the command descriptor.
    def set_command_address(addr: UInt): Unit = command_temp.address = addr

    // Controls the assertion or deassertion of the arbiterlock interface signal
    // Not used in Burst mode
    def set_command_arbiterlock(state: Boolean): Unit = {
        if(USE_BURSTCOUNT == 0) command_temp.arbiterlock = state
        else {
            printf(p"Cannot use arbiterlock in Burst mode, set failed")
            command_temp.arbiterlock = false
        }
    }

    // Sets the value driven on the Avalon interface burstcount pin
    def set_command_burst_count(burst_count: UInt): Unit = {
        command_temp.burstcount = burst_count
        if(USE_BURSTCOUNT == 0) {
            printf(p"Burst count Pin unavailable")
            command_temp.burstcount = 1.U
        }
        if(burst_count > MAX_BURST_SIZE)
            printf(p"Out of range, burst count should smaller than $MAX_BURST_SIZE\n")
        else if( burst_count < 1) printf(p"Out of range, burst count should larger than 0")

    }

    // Sets the transaction burst count in the command descriptor to determine the number of
    // words driven on the write burst comman
    def set_command_burst_size(burst_size: Int): Unit = command_temp.burst_size = burst_size

    // Sets the transaction write data in the command descriptor. Push into Vector
    def set_command_data(data: Int, index: Int): Unit = command_temp.writedata(index) = data
    // Controls the assertion or deassertion of the debugaccess interface signal.
    def set_command_debugaccess(state: Boolean) = command_temp.debugacess = state
    // Sets idle cycles at the end of each transaction cycle
    def set_command_idle(idle: Int, index: Int): Unit = command_temp.idle(index) = idle
    // Sets the number of cycles to postpone the start of a command.
    def set_command_init_latency(cycles: Int): Unit ={
        if(cycles > 0) command_temp.init_latency = cycles
        else printf(p"the number of cycles to postpone should larger than 0")
    }
    def set_command_lock(state: Boolean): Unit ={
        if(USE_BURSTCOUNT == 0) command_temp.lock = state
        else {
            printf(p"Cannot use arbiterlock in Burst mode, set failed")
            command_temp.lock = false
        }
    }

    def set_command_request(request: request_t.Type): Unit = command_temp.request = request
    // Sets the number of elapsed cycles between waiting for a waitrequest and when time out
    // is asserte
    def set_command_timeout(cycles: Int): Unit ={
        command_timeout = cycles
        if(cycles == 0) {
            printf(p"TImeout disabled \n")
        }
    }
    def set_command_write_response_request(request:Boolean): Unit = command_temp.write_response_valid = request
    // Sets the pending command queue size maximum threshold
    def set_max_command_queue_size(size:Int): Unit = max_command_queue_size = size

    def set_min_command_queue_size(size: Int): Unit = min_command_queue_size = size

    def set_response_timeout(cycles: Int): Unit = {
        response_timeout = cycles
        if(cycles == 0) {
            printf(p"TImeout disabled \n")
        }
    }
    // ********** Sophomore ***********////
    var signal_all_transactions_complete = 0
    var signal_command_issued = 0
    var signal_fatal_error = 0
    var signal_max_command_queue_size = 0
    var signal_min_command_queue_size = 0
    var signal_read_response_complete = 0
    var signal_response_complete = 0
    var signal_write_response_complete = 0
    ///********************** END *************************///
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

    def avalon_mm_write( addr_value: Int,
                         data_value: Int,
                         byteenable: Int ){
        //Before this point, some check points are set up
        //return the normalized data into IF
        //clock is not explicitly shown in chisel, so ignore the clock signal
        when(avm_if.rst.toBool()){
            init()
        }
        // Counting time, timeout
        when(command_timeout.U =/= clock_counter){
            clock_counter := RegNext(clock_counter + 1)
        } otherwise{ clock_counter := 0.U }
        //check flags
        if(USE_BURSTCOUNT == 1) {
            printf(p"Burst enabled, checking: \n")
            if(USE_READ_DATA_VALID == 1) printf(p"Readdatavalid is enabled \n")
            else printf(p"Failed: Readdatavalid should be enabled \n")
            //check data width
            if(AV_BURSTCOUNT_W <1 || AV_BURSTCOUNT_W > 11)
                printf(p"The burst data_w should within 1-11\n")
        }

        if(pending_command_queue.nonEmpty){
            command_cur = pending_command_queue.dequeue()
            clock_counter := 0.U
            while(clock_counter != command_cur.init_latency.U){
               avm_if.clk.step()
            }
            avm_if.avm_address     := command_cur.address
            avm_if.avm_burstcount  := command_cur.burstcount
            avm_if.avm_lock        := command_cur.lock.B
            avm_if.avm_debugaccess := command_cur.debugacess.B
            command_cur.request match{
                case REQ_READ =>  avm_if.avm_write := 0.U
                                  avm_if.avm_read  := 1.U
                                  avm_if.avm_writeresponsevalid := 0.U
                case REQ_WRITE => avm_if.avm_write := 1.U
                                  avm_if.avm_read  := 0.U
                                  avm_if.avm_writeresponsevalid := command_cur.write_response_valid.B
                case REQ_IDLE =>  avm_if.avm_write := 0.U
                                  avm_if.avm_read  := 0.U
                                  avm_if.avm_writeresponsevalid := 0.U
                case _ => printf(p"Warning: not a valid request \n")
            }
            command_issued_counter += 1
            // Output data from writedata port
            var start_time, end_time = clock_counter
            // waiting for wairrequest
            when(!avm_if.avm_waitrequest) {
                avm_if.clk.step()
            }
            for(i <- 0 until command_cur.burst_size){
                start_time = clock_counter
                if(command_cur.request == REQ_WRITE){
                    avm_if.avm_writedata := command_cur.writedata(i).U
                    avm_if.avm_write := true.B
                }
                avm_if.avm_byteenable := command_cur.byte_enable(i).U
                avm_if.clk.step()

                end_time = clock_counter
                wait_time_stamp = (clock_counter - start_time).intValue()
                issued_command_queue.command = command_cur
                issued_command_queue.wait_time(i) = wait_time_stamp
                issued_command_queue.time_stamp(i) = end_time.intValue()
                response_time_stamp_queue += clock_counter
            }
            if(USE_WRITERESPONSE == 0 || !command_cur.write_response_valid){
                completed_command = issued_command_queue

                completed_write_response.seq_count =
                    completed_command.command.seq_count
                completed_write_response.request =
                    completed_command.command.request
                completed_write_response.address =
                    completed_command.command.address
                completed_write_response.byte_enable =
                    completed_command.command.byte_enable
                completed_write_response.burstcount =
                    completed_command.command.burstcount
                completed_write_response.burst_size =
                    completed_command.command.burst_size
                completed_write_response.data =
                    completed_command.command.writedata
                completed_write_response.wait_latency =
                    completed_command.wait_time

                completed_write_response.write_response_valid = false
                completed_write_response.write_response  = response_status.OKAY

                pending_write_response_queue += completed_write_response
            }

        }


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

    
}