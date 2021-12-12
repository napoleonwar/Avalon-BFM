;buildInfoPackage: chisel3, version: 3.4.1, scalaVersion: 2.12.12, sbtVersion: 1.3.10
circuit Mux5 : 
  module Mux5 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip a : UInt<8>, flip b : UInt<8>, flip c : UInt<8>, flip d : UInt<8>, flip e : UInt<8>, flip sel : UInt<3>, y : UInt<8>}
    
    io.y <= UInt<1>("h01") @[Mux5.scala 19:8]
    node _T = eq(UInt<1>("h00"), io.sel) @[Conditional.scala 37:30]
    when _T : @[Conditional.scala 40:58]
      io.y <= io.a @[Mux5.scala 21:25]
      skip @[Conditional.scala 40:58]
    else : @[Conditional.scala 39:67]
      node _T_1 = eq(UInt<1>("h01"), io.sel) @[Conditional.scala 37:30]
      when _T_1 : @[Conditional.scala 39:67]
        io.y <= UInt<1>("h01") @[Mux5.scala 22:25]
        skip @[Conditional.scala 39:67]
      else : @[Conditional.scala 39:67]
        node _T_2 = eq(UInt<2>("h02"), io.sel) @[Conditional.scala 37:30]
        when _T_2 : @[Conditional.scala 39:67]
          io.y <= io.c @[Mux5.scala 23:25]
          skip @[Conditional.scala 39:67]
        else : @[Conditional.scala 39:67]
          node _T_3 = eq(UInt<2>("h03"), io.sel) @[Conditional.scala 37:30]
          when _T_3 : @[Conditional.scala 39:67]
            io.y <= io.d @[Mux5.scala 24:25]
            skip @[Conditional.scala 39:67]
          else : @[Conditional.scala 39:67]
            node _T_4 = eq(UInt<3>("h05"), io.sel) @[Conditional.scala 37:30]
            when _T_4 : @[Conditional.scala 39:67]
              io.y <= io.e @[Mux5.scala 25:25]
              skip @[Conditional.scala 39:67]
    
