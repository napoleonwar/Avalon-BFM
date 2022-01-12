package avalon_mm

import chisel3.Bundle

//Parameters, including setup time, wait time....timing defines
//In the Verification IP User Guide
//they are constants for the rest, so defined as an object
object avalon_mm_config extends Bundle {
  // Port Width
  val AV_ADDRESS_W = 32 // Address width in bits
  val AV_SYMBOL_W = 8 // Data symbol width in bits
  val AV_READRESPONSE_W = 8 // Read response signal width in bits
  val AV_WRITERESPONSE_W = 8 // Write response signal width in bits
  // Parameters
  val AV_NUMSYMBOLS = 4 // Number of symbols per word
  val AV_BURSTCOUNT_W = 3 // Burst port width in bits
  // Port Enables (ON/OFF)
  val USE_READ = 1 // Use read pin on interface
  val USE_WRITE = 1 // Use write pin on interface
  val USE_ADDRESS = 1 // Use address pins on interface
  val USE_BYTE_ENABLE = 1 // Use byteenable pins on interface
  val USE_BURSTCOUNT = 1 // Use burstcount pin on interface
  val USE_READ_DATA = 1 // Use readdata pin on interface
  val USE_READ_DATA_VALID = 1 // Use readdatavalid pin on interface
  val USE_WRITE_DATA = 1 // Use writedata pin on interface
  val USE_BEGIN_TRANSFER = 0 // Use begintransfer pin on interface
  val USE_BEGIN_BURST_TRANSFER = 0 // Use beginbursttransfer pin on interface
  val USE_ARBITERLOCK = 0 // Use arbiterlock pin on interface
  val USE_LOCK = 0 // Use lock pin on interface
  val USE_DEBUGACCESS = 0 // Use debugaccess pin on interface
  val USE_WAIT_REQUEST = 1 // Use waitrequest pin on interface
  val USE_TRANSACTIONID = 0 // Use transactionid interface pin
  val USE_WRITERESPONSE = 0 // Use writeresponse interface pins
  val USE_READRESPONSE = 0 // Use readresponse interface pins
  val USE_CLKEN = 0 // Use clken interface pins
  // Port Polarity (assert signal HIGH/LOW)

  // Burst Attributes
  val AV_BURST_LINEWRAP = 1 // Wrapping burst. Only LSB is used
  val AV_BURST_BNDR_ONLY = 1 // Assert Addr alignment
  // Miscellaneous
  val AV_MAX_PENDING_READS = 1 // Max number of pending reads transactions can be queued by slave
  val AV_FIX_READ_LATENCY = 1 // USE_READ_DATA_VALID = 0. Read latency for fixed latency slave (cycles).
  val VHDL_ID = 0 // For VHDL only. VHDL BFM ID number
  // Timing
  val AV_FIXED_READ_WAIT_TIME = 1 // USE_WAIT_REQUEST is 0. Fixed wait time cycles when
  val AV_FIXED_WRITE_WAIT_TIME = 0 // USE_WAIT_REQUEST is 0
  val AV_REGISTERED_WAITREQUEST = 0 // Waitrequest is registered at the slave. To Reg
  val AV_REGISTERINCOMINGSIGNALS = 0 // Indicate that waitrequest is come from register

  val AV_MAX_PENDING_WRITES = 0 // Number of pending write transactions
  val AV_CONSTANT_BURST_BEHAVIOR = 1 // Address, burstcount, transactionid and avm_writeresponserequest
  val MAX_BURST_SIZE = if (USE_BURSTCOUNT != 0) 2 ^ (AV_BURSTCOUNT_W - 1) else 1
  val AV_DATA_W = AV_SYMBOL_W * AV_NUMSYMBOLS
  val AV_TRANSACTIONID_W = 8


}
