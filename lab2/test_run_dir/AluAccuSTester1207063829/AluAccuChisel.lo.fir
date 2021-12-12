circuit AluAccuChisel :
  module AluAccuChisel :
    input clock : Clock
    input reset : UInt<1>
    input io_op : UInt<3>
    input io_din : UInt<128>
    input io_ena : UInt<1>
    output io_accu : UInt<128>
  
    reg a : UInt<128>, clock with :
      reset => (UInt<1>("h0"), a) @[AluAccuChisel.scala 17:24]
    node _T = eq(UInt<3>("h0"), io_op) @[Conditional.scala 37:30]
    node _T_1 = eq(UInt<3>("h1"), io_op) @[Conditional.scala 37:30]
    node _T_2 = add(a, io_din) @[AluAccuChisel.scala 29:16]
    node _T_3 = tail(_T_2, 1) @[AluAccuChisel.scala 29:16]
    node _T_4 = eq(UInt<3>("h2"), io_op) @[Conditional.scala 37:30]
    node _T_5 = sub(a, io_din) @[AluAccuChisel.scala 32:16]
    node _T_6 = tail(_T_5, 1) @[AluAccuChisel.scala 32:16]
    node _T_7 = eq(UInt<3>("h3"), io_op) @[Conditional.scala 37:30]
    node _T_8 = and(a, io_din) @[AluAccuChisel.scala 35:16]
    node _T_9 = eq(UInt<3>("h4"), io_op) @[Conditional.scala 37:30]
    node _T_10 = or(a, io_din) @[AluAccuChisel.scala 38:16]
    node _T_11 = eq(UInt<3>("h5"), io_op) @[Conditional.scala 37:30]
    node _T_12 = xor(a, io_din) @[AluAccuChisel.scala 41:16]
    node _T_13 = eq(UInt<3>("h7"), io_op) @[Conditional.scala 37:30]
    node _T_14 = shr(a, 1) @[AluAccuChisel.scala 44:16]
    node _T_15 = eq(UInt<3>("h6"), io_op) @[Conditional.scala 37:30]
    node _GEN_0 = mux(_T_15, io_din, a) @[Conditional.scala 39:67]
    node _GEN_1 = mux(_T_13, _T_14, _GEN_0) @[Conditional.scala 39:67]
    node _GEN_2 = mux(_T_11, _T_12, _GEN_1) @[Conditional.scala 39:67]
    node _GEN_3 = mux(_T_9, _T_10, _GEN_2) @[Conditional.scala 39:67]
    node _GEN_4 = mux(_T_7, _T_8, _GEN_3) @[Conditional.scala 39:67]
    node _GEN_5 = mux(_T_4, _T_6, _GEN_4) @[Conditional.scala 39:67]
    node _GEN_6 = mux(_T_1, _T_3, _GEN_5) @[Conditional.scala 39:67]
    node _GEN_7 = mux(_T, a, _GEN_6) @[Conditional.scala 40:58]
    node res = _GEN_7 @[AluAccuChisel.scala 26:11 AluAccuChisel.scala 29:11 AluAccuChisel.scala 32:11 AluAccuChisel.scala 35:11 AluAccuChisel.scala 38:11 AluAccuChisel.scala 41:11 AluAccuChisel.scala 44:11 AluAccuChisel.scala 47:11]
    node _GEN_8 = mux(io_ena, res, a) @[AluAccuChisel.scala 51:17]
    io_accu <= a @[AluAccuChisel.scala 55:11]
    a <= mux(reset, UInt<128>("h0"), _GEN_8) @[AluAccuChisel.scala 52:13]
