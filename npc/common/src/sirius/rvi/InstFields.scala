package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import cpuutil.CanAutoGenSig

object InstFieldsRvI {
  val fields = Seq(
    MakeBoolField("isEbreak", "debug", _.name == "ebreak"),
    new BoolDecodeField[InstPattern] with CanAutoGenSig {
      def name = "isWriteBackReg"
      def stage = "wb"
      def genTable(i: InstPattern) = i.writeBackSel match {
        case DontCare => n
        case _        => y
      }
    },
    new BoolDecodeField[InstPattern] with CanAutoGenSig {
      def name = "isBranch"
      def stage = "wb"
      def genTable(i: InstPattern) =
        if (i.isBranch) { y }
        else { n }
    },
    new BoolDecodeField[InstPattern] with CanAutoGenSig {
      def name = "isJump"
      def stage = "wb"
      def genTable(i: InstPattern) =
        if (i.isJump) { y }
        else { n }
    },
    new BoolDecodeField[InstPattern] with CanAutoGenSig {
      def name = "isLoad"
      def stage = "ls"
      def genTable(i: InstPattern) = i.loadStoreType match {
        case LoadStoreTypeEnum.signedLoad   => y
        case LoadStoreTypeEnum.unsignedLoad => y
        case _                              => n
      }
    },
    new BoolDecodeField[InstPattern] with CanAutoGenSig {
      def name = "isStore"
      def stage = "ls"
      def genTable(i: InstPattern) = i.loadStoreType match {
        case LoadStoreTypeEnum.store => y
        case _                       => n
      }
    },
    new BoolDecodeField[InstPattern] with CanAutoGenSig {
      def name = "isUnsignedLoad"
      def stage = "ls"
      def genTable(i: InstPattern) = i.loadStoreType match {
        case LoadStoreTypeEnum.unsignedLoad => y
        case LoadStoreTypeEnum.signedLoad   => n
        case _                              => dc
      }
    },
    MakeEnumField("loadStoreLength", "ls", LoadStoreLengthEnum, _.loadStoreLength),
    MakeEnumField("aluIn2Sel", "ex", AluInSelEnum, _.aluIn2Sel),
    MakeEnumField("aluOp", "ex", AluOpEnum, _.aluOp),
    MakeEnumField("exuOutSel", "ex", ExuOutSelEnum, _.exuOutSel),
    MakeEnumField("instType", "id", InstTypeEnum, _.instType),
    MakeEnumField("jumpTargetSel", "wb", JumpTargetSelEnum, _.jumpTargetSel),
    MakeEnumField("writeBackSel", "wb", WriteBackSelEnum, _.writeBackSel),
  )
}
