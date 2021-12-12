module Mux5(
  input        clock,
  input        reset,
  input  [7:0] io_a,
  input  [7:0] io_b,
  input  [7:0] io_c,
  input  [7:0] io_d,
  input  [7:0] io_e,
  input  [2:0] io_sel,
  output [7:0] io_y
);
  wire  _T = 3'h0 == io_sel; // @[Conditional.scala 37:30]
  wire  _T_1 = 3'h1 == io_sel; // @[Conditional.scala 37:30]
  wire  _T_2 = 3'h2 == io_sel; // @[Conditional.scala 37:30]
  wire  _T_3 = 3'h3 == io_sel; // @[Conditional.scala 37:30]
  wire  _T_4 = 3'h5 == io_sel; // @[Conditional.scala 37:30]
  wire [7:0] _GEN_0 = _T_4 ? io_e : 8'h1; // @[Conditional.scala 39:67 Mux5.scala 25:25 Mux5.scala 19:8]
  wire [7:0] _GEN_1 = _T_3 ? io_d : _GEN_0; // @[Conditional.scala 39:67 Mux5.scala 24:25]
  wire [7:0] _GEN_2 = _T_2 ? io_c : _GEN_1; // @[Conditional.scala 39:67 Mux5.scala 23:25]
  wire [7:0] _GEN_3 = _T_1 ? 8'h1 : _GEN_2; // @[Conditional.scala 39:67 Mux5.scala 22:25]
  assign io_y = _T ? io_a : _GEN_3; // @[Conditional.scala 40:58 Mux5.scala 21:25]
endmodule
