package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import cpuutil.CanAutoGenSig

object MakeEnumField {
  def apply[T <: ChiselEnum](
    fieldName:  String,
    fieldStage: String,
    chiselEnum: T,
    patternField: (InstPattern) => Data
  ): DecodeField[InstPattern, UInt] with CanAutoGenSig = {
    new DecodeField[InstPattern, UInt] with CanAutoGenSig {
      def name = fieldName
      def stage = fieldStage
      def chiselType = UInt(chiselEnum.getWidth.W)
      def genTable(i: InstPattern) = patternField(i) match {
        case e: chiselEnum.Type => BitPat(e)
        case _ => dc
      }
    }
  }
}

object MakeBoolField {
  def apply[T <: ChiselEnum](
    fieldName:  String,
    fieldStage: String,
    patternField: (InstPattern) => Boolean
  ): BoolDecodeField[InstPattern] with CanAutoGenSig = {
    new BoolDecodeField[InstPattern] with CanAutoGenSig {
      def name = fieldName
      def stage = fieldStage
      def genTable(i: InstPattern) = 
        if (patternField(i)) {y}
        else {n}
    }
  }
}

object InstFields {
  val fieldRvI = InstFieldsRvI.fields
}
