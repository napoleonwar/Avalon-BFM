package avalon_mm

import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.iotesters._
import avalon_mm_config._
import chisel3.util.Enum
import avalon_mm.request_t._
import avalon_mm.response_status._

import scala.collection.mutable

 class avalon_mm_bfm_slave_if(val data_w: Int, val addr_w: Int, burst_w: Int) extends Bundle {
  // Clock and Reset????
  val clk              = Input(Clock())
  val rst              = Input(Reset())
  val clken            = Input(Bool())
  //Write OUTPUT Host -> agent
  //Read INPUT Agent -> Host
  val avs_address     = Input(UInt(data_w.W))
  val avs_byteenable  = Input(UInt((data_w / 8).W))
  val avs_debugaccess = Input(Bool()) //for Nios II processor
  val avs_read        = Input(Bool())
  val avs_readdata    = Output(UInt(data_w.W))
  val avs_response    = Output(UInt(2.W))
  val avs_write       = Input(Bool())
  val avs_writedata   = Input(UInt(data_w.W))
  val avs_lock        = Input(Bool())
  val avs_waitrequest = Output(Bool())
  // Pipelined
  val avs_readdatavalid       = Output(Bool())
  val avs_writeresponsevalid  = Output(Bool())
  //Write Host -> Agent Burst
  val avs_burstcount  = Output(UInt(burst_w.W))
  // There are some pins not shown in the specification but in IP guide
  // Need to be added later
  val avs_transactionid = Input(UInt(AV_TRANSACTIONID_W.W))
  val avs_readid        = Output(UInt(AV_TRANSACTIONID_W.W))
  val avs_writeid       = Output(UInt(AV_TRANSACTIONID_W.W))
}
object avalon_mm_config {
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
 val AV_MAX_PENDING_READS    = 1 // Max number of pipelined pending reads transactions can be queued by slave
 val VHDL_ID                 = 0 // For VHDL only. VHDL BFM ID number
 // Timing
 val AV_FIXED_READ_LATENCY       = 0
 val AV_FIXED_READ_WAIT_TIME     = 1 // USE_WAIT_REQUEST is 0. Fixed wait time cycles w
 val AV_FIXED_WRITE_WAIT_TIME    = 0 // USE_WAIT_REQUEST is 0
 val AV_REGISTERED_WAITREQUEST   = 1 // Waitrequest is registered at the slave. To Reg
 val AV_REGISTERINCOMINGSIGNALS  = 1 // Indicate that waitrequest is come from register

 val AV_MAX_PENDING_WRITES       = 0 // Number of pending write transactions
 val AV_CONSTANT_BURST_BEHAVIOR  = 1 // Address, burstcount, transactionid and avm_writeresponserequest
 val MAX_BURST_SIZE  = if (USE_BURSTCOUNT != 0) 2 ^ (AV_BURSTCOUNT_W - 1) else 1
 val AV_DATA_W       = AV_SYMBOL_W * AV_NUMSYMBOLS
 val AV_TRANSACTIONID_W = 8
}
//Command and Response Descriptor
class SlaveCommand_t {
 var request             = REQ_IDLE
 var address             = UInt((AV_ADDRESS_W).W) // start address
 var burst_count         = UInt((AV_BURSTCOUNT_W).W) // the burst data length
 var data                = Array.ofDim[Int](MAX_BURST_SIZE)  // data point to agent, 32 bits for each
 var byte_enable         = Array.ofDim[Byte](MAX_BURST_SIZE) // byte choose, 4 bits for each
 var idle                = Array.ofDim[Int](MAX_BURST_SIZE) // ?????what is this?????, 32 bits for each
 var burst_size          = MAX_BURST_SIZE
 var arbiterlock         = false
 var lock                = false
 var debugacess          = false
 val transaction_id      = UInt((AV_TRANSACTIONID_W).W)
 var write_response_valid = false

}

class SlaveResponse_t {
 var request = REQ_IDLE
 var address = UInt((AV_ADDRESS_W).W) // start address
 var burst_count = UInt((AV_BURSTCOUNT_W).W) // the burst data length
 var data = Array.ofDim[Int](MAX_BURST_SIZE) // data point to agent, 32 bits for each
 var response_latency = Array(MAX_BURST_SIZE)
 var read_id             = UInt((AV_TRANSACTIONID_W).W)
 var write_id            = UInt((AV_TRANSACTIONID_W).W)
 var response       = Array.ofDim[Value](MAX_BURST_SIZE)
}

class IssuedCommand_t {
 var command = new SlaveCommand_t
  var time_stamp = 0
}


// ************************ The main function ******************* //
class avalon_mm_bfm_slave_main[T <: MultiIOModule]( val avm_if: avalon_mm_bfm_slave_if) extends PeekPokeTester(dut) {
  //*********************** Set up Registers about timing ******//
  val clock_counter = RegInit(0.U)
  val clken_register                   = RegInit(true.B)
  val signal_fatal_error = RegInit(false.B)
  val signal_error_exceed_max_pending_reads = RegInit(false.B)
  val signal_error_exceed_max_pending_writes = RegInit(false.B)
  val signal_command_received = RegInit(false.B)
  val signal_response_issued  = RegInit(false.B)
  val signal_max_response_queue_size = RegInit(false.B)
  val signal_min_response_queue_size = RegInit(false.B)

  //********************** Set up some globle variables*********//
  var slave_is_full                    = false
  var slave_cannot_take_in_more_reads  = false
  var slave_cannot_take_in_more_writes = false
  var response_is_valid                = false
  var pending_read_counter             = 0
  var pending_write_counter            = 0
  var required_response_latency        = 0
  var response_timeout                 = 100  // disabled when 0
  var max_response_queue_size          = 256
  var min_response_queue_size          = 2
  var consolidate_write_burst_transactions = 1
  var idle_output_config = 0
  var idle_waitrequest_config = if(AV_REGISTERINCOMINGSIGNALS == 1) 1; else 0
  var default_waitrequest_idle = if(AV_REGISTERINCOMINGSIGNALS == 1) 1; else 0

  // Command Descriptor <- Command Queue <- Monitor
  //                             |             |
  //                         Command Queue | Fabric BUs
  //                             |             |
  // Response Descriptor -> Response Queue -> Driver
  //************************* Queues *********************************//
  // Build up necessary types for Command/Response queue
  // Instantiate Queues and Descriptors
  // 3 main function queues
  val command_queue = mutable.Queue[SlaveCommand_t]()
  val response_queue = mutable.Queue[SlaveResponse_t]()
  val issued_command_queue = mutable.Queue[IssuedCommand_t]()
  // These are Commands from host and response from agent object
  var current_command = new SlaveCommand_t
  var client_command = new SlaveCommand_t
  var client_response = new SlaveResponse_t
  var construct_issued_command = new IssuedCommand_t

  // Timing container, each
  var read_wait_time = Array.ofDim[Int](MAX_BURST_SIZE)
  var write_wait_time = Array.ofDim[Int](MAX_BURST_SIZE)
  ///********************* API Functions **********************///
  // From here, 50+ BFM functions illustrated in Verification IP Guide
  // will be implemented, still have some questions about DUT-IF connection
  def get_clken(){}

  // Query the Received Descriptor:
  // The transaction addr
  def get_command_address(){}
  // The transaction Arbiterlock
  def get_command_arbiterlock(): boolean{}
  // Transaction Burst count
  def get_command_burst_count()
  def get_command_burst_cycle()
  def set_response_timeout(cycles: Int = 100): Unit = response_timeout = cycles
  def get_command_queue_size(): Int = command_queue.size
  def get_response_queue_size(): Int = response_queue.size
  // How many cycles will it react to a read/write command after received
  def set_response_latency(latency: Int, index: Int): Unit ={
    if(client_response.request == REQ_READ) {
      if (USE_READ_DATA_VALID == 0) printf(p"Slave has fixed latency \n")
      else client_response.response_latency(index) = latency
    }
    else {
      if(USE_WRITERESPONSE == 1)
        if(index == 0) client_response.response_latency(index) = latency
        else printf(p"Write response is disabled \n")
    }
  }

  def set_response_bursts_size(burst_size: Int): Unit ={
    if(burst_size > MAX_BURST_SIZE) printf(p" The input burst size is larger than MAX")
    else client_response.burst_count = burst_size.U
  }
  //
  def set_response_data(data: Int, index: Int): Unit = client_response.data(index) = data
  // Push the ready response from Descriptor into the queue, will transfer it as soon as possible
  def push_response(): Unit ={
    expect(avm_if.rst.asUInt(), 0)
    if(USE_WRITERESPONSE == 1 || client_response.request == REQ_READ){
      response_queue += client_response
    }
  }
  // Remove the command Descriptor from the queue, the testbench can use it
  def pop_command: Unit ={
    if(avm_if.rst.asBool() == true) printf(p"Reset is evoked, cannot do pop now \n")
    expect(avm_if.rst.asUInt(), 0)
    client_command = command_queue.dequeue()
  }

  def get_command_request(): request_t = client_command.request
  def get_command_address(): UInt = client_command.address
  def get_command_burst_count(): UInt = client_command.burst_count
  def get_command_data(index: Int): Int = client_command.byte_enable(index)
  def get_command_byte_enable(index: Int): Int = client_command.byte_enable(index)
  def get_command_burst_cycle(): Int = client_command.burst_size
  // waiting states by driving waitrequest. Write each burst has to wait. Read only needs the first one
  def set_interface_wait_time(wait_cycles: Int, index : Int): Unit ={
    if(USE_WAIT_REQUEST == 0) {
      printf(p"WAIT REQUEST IS DISABLED. Only fixed wait latency can be used \n")
      return
    }
    else if(AV_REGISTERINCOMINGSIGNALS == 1 && wait_cycles == 0) {
      printf(p"WAITREQUEST is coming from Reg, should set wait_cycles at least 1")
    }
    else {
      read_wait_time(index) = wait_cycles
      write_wait_time(index) = wait_cycles
      printf(p"set wait cycles successfully \n")
    }
  }
  // Mode = 0, Consolidated, single command TR contains wr data for all burst cycles in that command
  // Mode = 1, yield one command TR per burst cycle
  def set_command_transaction_mode(mode: Int): Unit ={
    consolidate_write_burst_transactions = ~mode
  }
  def get_command_arbiterlock(): Boolean = client_command.lock
  def get_command_lock(): Boolean = client_command.lock
  def get_command_debugaccess(): Boolean = client_command.debugacess
  def set_max_response_queue_size(size: Int): Unit = max_response_queue_size = size
  def set_min_response_queue_size(size: Int): Unit = min_response_queue_size = size
  // no transaction id
  def get_command_write_response_request(): Int = USE_WRITERESPONSE
  def set_response_request(request: request_t): Unit = client_response.request = request

  def set_read_response_status(status: response_status, index: Int): Unit ={
    if(client_response.response == REQ_READ){
      if(USE_READRESPONSE == 1)
        client_response.response(index) = status
      else printf(p"Read response is disabled \n")
    }
    else printf(p"Read response set on write response transaction \n")
  }

  def set_write_response_status(status: response_status, index: Int): Unit ={
    if(client_response.request == REQ_WRITE){
      if(USE_WRITERESPONSE == 1)
        client_response.response(index) = status
      else printf(p"Write response is disabled \n")
    }
    else printf(p"Write response set on read response transaction \n")
  }

  def set_read_response_id(id: Int): Unit = client_response.read_id = id.U //avs_read_id = id

  def set_write_response_id(id: Int): Unit = client_response.write_id = id.U
  // check if the slave is full, 1 is full of pending transactions, 0 is not
  def get_slave_bfm_status(): Boolean = slave_is_full

  def get_pending_read_lantency_cycle(): Int = get_pending_response_latency_cycle()
  def get_pending_response_latency_cycle(): Int = required_response_latency
  def get_clken(): Boolean = clken_register.litToBoolean

  def get_pending_read_transaction(): Int = pending_read_counter
  def get_pending_write_transaction(): Int = pending_write_counter

  // Privat functions
  def __init_descriptors(): Unit ={
    pending_read_counter = 0
    pending_write_counter = 0
    slave_is_full = false
    response_is_valid = false
    client_command = new SlaveCommand_t
    client_response = new SlaveResponse_t
  }

  def __init_queues(): Unit ={
    command_queue.clear()
    issued_command_queue.clear()
    response_queue.clear()
  }
  def __drive_read_response_idle(): Unit ={
    avm_if.avs_readdatavalid := false.B
    if(idle_output_config == 1){
      avm_if.avs_readdata := 0.U
      avm_if.avs_readid := 0.U
      if(!avm_if.avs_writeresponsevalid.litToBoolean){
        avm_if.avs_response := 0.U
      }
    }
    else {
      avm_if.avs_readdata := 1.U
      avm_if.avs_readid := 1.U
      if(!avm_if.avs_writeresponsevalid.litToBoolean){
        avm_if.avs_response := 1.U
      }
    }
  }

  def __drive_write_response_idle(): Unit = {
    avm_if.avs_writeresponsevalid := false.B
    if(idle_output_config == 0){
      avm_if.avs_writeid := 0.U
    }
    else avm_if.avs_writeid := 1.U
  }

  def __drive_response_idle(): Unit ={
    __drive_read_response_idle()
    __drive_write_response_idle()
  }

  def __drive_waitrequest_idle(): Unit ={
    if(!res)
  }

  def __reset(): Unit ={
    __drive_response_idle()
    __init_queues()
    __init_descriptors()
    avm_if.avs_waitrequest := false.B
  }
  def construct_command(command_t: SlaveCommand_t, i: Int): SlaveCommand_t ={
    if(avm_if.avs_read.litToBoolean){
      command_t.request = REQ_READ
      command_t.byte_enable(0) = avm_if.avs_byteenable
    }
    else if(avm_if.avs_write.litToBoolean){
      command_t.request = REQ_WRITE
      command_t.data(i) = avm_if.avs_writedata.toInt
      command_t.byte_enable(i) = avm_if.avs_byteenable
    }
    else{
      printf(p"Cannot construct unknown command received \n")
    }
    command_t.arbiterlock = avm_if.avs
    command_t
  }



  ////////////////////////////////////////////////
  // Initialization program in scala, later the
  // Program should first run the this part, then
  // it goes to the 'always' block in sv
  //////////////////////////////////////////////
  if (USE_READ_DATA_VALID == 0 && USE_BURSTCOUNT > 0 && USE_READ == 1)
    printf(p"USE_READ_DATA_VALID must be enabled if USE_READ and USE_BURSTCOUNT enabled\n")
  if (USE_BURSTCOUNT > 0 &&
    (AV_BURSTCOUNT_W < 1 || AV_BURSTCOUNT_W > 11)) 
    printf(p"Illegal AV_BURSTCOUNT_W specified - range must be [1..11]\n")

  if (USE_WAIT_REQUEST == 0) 
    for (i <- 0 until MAX_BURST_SIZE) {
        read_wait_time(i) =  AV_FIXED_READ_WAIT_TIME
        write_wait_time(i) =  AV_FIXED_WRITE_WAIT_TIME
      }
  else if (AV_REGISTERINCOMINGSIGNALS == 1)
    for (i <- 0 until MAX_BURST_SIZE){
      read_wait_time(i) = 1
      write_wait_time(i) = 1
    }

  if(response_queue.size > max_response_queue_size) 
    signal_max_response_queue_size := true.B
  else if(response_queue.size < min_response_queue_size)
    signal_min_response_queue_size := true.B

  when(avm_if.clk.asBool()){
    when(clken_register) {
      clock_counter := clock_counter + 1.U
    }
  }

  // Bus monitor
  withReset(avm_if.rst){
    __reset()
  }
  withClock(avm_if.clk){
    clken_register := avm_if.clken
  }
  withClockAndReset(avm_if.clk, avm_if.rst){

  }
  // Checking basic parameters violation
  if((pending_read_counter < AV_MAX_PENDING_READS) || (AV_MAX_PENDING_READS == 0)){
    slave_is_full = false
    slave_cannot_take_in_more_reads = false
  }
  else{
    slave_cannot_take_in_more_reads = true
    if(pending_read_counter > AV_MAX_PENDING_READS){
      printf(p"Pipelined read commands exceed MAX \n")
      signal_error_exceed_max_pending_reads := true.B
    }
  }
  if(USE_WRITERESPONSE == 1){
    if((pending_write_counter < AV_MAX_PENDING_WRITES) || AV_MAX_PENDING_READS == 0){
      slave_is_full = false
      slave_cannot_take_in_more_writes = false
    }
    else {
      slave_cannot_take_in_more_writes = true
      if(pending_write_counter > AV_MAX_PENDING_WRITES){
        printf(p"Pipelined write commands exceed MAX \n")
        signal_error_exceed_max_pending_writes := true.B
      }
    }
  }
  else slave_cannot_take_in_more_writes = true
  if(slave_cannot_take_in_more_writes && slave_cannot_take_in_more_reads)
    slave_is_full = true

  var burst_mode = 0
  var addr_offset = 0
  // Write / read command
  if(avm_if.avs_read.litToBoolean && avm_if.avs_write.litToBoolean){
    current_command.address = avm_if.avs_address
    printf(p"Error: Write and read active at the same time \n")
    signal_fatal_error := 1.U
  }
  else if(avm_if.avs_write.litToBoolean){
    if(USE_BURSTCOUNT == 1 && burst_mode == 0){
      current_command = new SlaveCommand_t
      addr_offset = 0
      if(addr_offset == avm_if.avs_burstcount -1) burst_mode = 0
      else burst_mode = 1
    }
    else {
      if(burst_mode == 1){
        addr_offset += 1
        if(addr_offset == current_command.burst_count - 1) burst_mode = 0
      }
      else{
        current_command = new SlaveCommand_t
        burst_mode = 0
        addr_offset = 0
      }
    }
    construct_command(current_command, addr_offset)

    if((consolidate_write_burst_transactions == 1 &&
      (addr_offset == current_command.burst_count -1)) || consolidate_write_burst_transactions == 0){
      command_queue.enqueue(current_command)
      if(USE_WRITERESPONSE == 1){
        if((consolidate_write_burst_transactions == 0 &&
          (addr_offset == current_command.burst_count -1)) || consolidate_write_burst_transactions == 1){
          construct_issued_command.command = current_command
          construct_issued_command.time_stamp = clock_counter.toInt
          issued_command_queue.enqueue(construct_issued_command)
        }
    }
  }
}

class avalon_mm_slave_monitor(avm_if: avalon_mm_bfm_slave_if) extends avalon_mm_bfm_slave_main(avm_if){

}



