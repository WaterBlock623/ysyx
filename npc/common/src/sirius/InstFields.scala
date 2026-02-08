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

object InstFields {
  val fieldRvI = InstFieldsRvI.fields
}
