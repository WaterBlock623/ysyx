package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import cpuutil.CanAutoGenSig

object InstFieldsRvI {
  val fields = Seq(
    MakeBoolField("isEbreak", "debug", _.name == "ebreak"),
    MakeBoolField("isWriteBackReg", "wb", _.isWriteBackReg),
    MakeBoolField("isBranch", "wb", _.isBranch),
    MakeBoolField("isJump", "wb", _.isJump),
    MakeBoolField("isLoad", "ls", { 
      case LoadStoreTypeEnum.signedLoad   => true
      case LoadStoreTypeEnum.unsignedLoad => true
      case _                              => false
    }),
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
