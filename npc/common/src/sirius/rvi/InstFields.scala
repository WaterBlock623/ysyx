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
    MakeBoolField(
      "isLoad",
      "ls",
      _.loadStoreType match {
        case LoadStoreTypeEnum.signedLoad   => true
        case LoadStoreTypeEnum.unsignedLoad => true
        case _                              => false
      }
    ),
    MakeBoolField(
      "isStore",
      "ls",
      _.loadStoreType match {
        case LoadStoreTypeEnum.store => true
        case _                       => false
      }
    ),
    MakeTriField(
      "isUnsignedLoad",
      "ls",
      _.loadStoreType match {
        case LoadStoreTypeEnum.unsignedLoad => TriState.True
        case LoadStoreTypeEnum.signedLoad   => TriState.False
        case _                              => TriState.DontCare
      }
    ),
    MakeEnumField(
      "loadStoreLength",
      "ls",
      LoadStoreLengthEnum,
      _.loadStoreLength
    ),
    MakeEnumField("aluIn1Sel", "ex", AluInSelEnum, _.aluIn1Sel),
    MakeEnumField("aluIn2Sel", "ex", AluInSelEnum, _.aluIn2Sel),
    MakeEnumField("aluOp", "ex", AluOpEnum, _.aluOp),
    MakeEnumField("exuOutSel", "ex", ExuOutSelEnum, _.exuOutSel),
    MakeEnumField("jumpTargetSel", "ex", JumpTargetSelEnum, _.jumpTargetSel),
    MakeEnumField("instType", "id", InstTypeEnum, _.instType),
    MakeEnumField("writeBackSel", "wb", WriteBackSelEnum, _.writeBackSel),
    MakeBoolField("isWriteBackCsr", "wb", _.isWriteBackCsr),
  )
}
