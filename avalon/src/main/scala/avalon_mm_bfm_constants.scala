package avalon_mm
import chisel3._
import chisel3.util_

package object avalon_mm {
    object response_encoding {
        val OKAY = "b00".U
        val RESERVED = "B01".U
        val SLVERR = "b10".U
        val DECODEERROR = "b11".U
    }
}