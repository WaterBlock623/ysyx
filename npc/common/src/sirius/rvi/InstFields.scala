package sirius

import chisel3._
import chisel3.util.experimental.decode._
import chisel3.util.BitPat
import cpuutil.CanAutoGenSig

object InstFieldsRvI {
  val fields = Seq(
    new BoolDecodeField[InstPattern] with CanAutoGenSig {
      def name = "isEbreak"
      def stage = "debug"
      def genTable(i: InstPattern) = i.name match {
        case "ebreak" => y
        case _        => n
      }
    },
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
    new DecodeField[InstPattern, UInt] with CanAutoGenSig {
      def name = "loadStoreLength"
      def stage = "ls"
      def chiselType = UInt(LoadStoreLengthEnum.getWidth.W)
      def genTable(i: InstPattern) = i.loadStoreLength match {
        case e: LoadStoreLengthEnum.Type => BitPat(e)
        case _ => dc
      }
    },
    new DecodeField[InstPattern, UInt] with CanAutoGenSig {
      def name = "aluIn2Sel"
      def stage = "ex"
      def chiselType = UInt(AluInSelEnum.getWidth.W)
      def genTable(i: InstPattern) = i.aluIn2Sel match {
        case e: AluInSelEnum.Type => BitPat(e)
        case _ => dc
      }
    },
    new DecodeField[InstPattern, UInt] with CanAutoGenSig {
      def name = "aluOp"
      def stage = "ex"
      def chiselType = UInt(AluOpEnum.getWidth.W)
      def genTable(i: InstPattern) = i.aluOp match {
        case e: AluOpEnum.Type => BitPat(e)
        case _ => dc
      }
    },
    new DecodeField[InstPattern, UInt] with CanAutoGenSig {
      def name = "exuOutSel"
      def stage = "ex"
      def chiselType = UInt(ExuOutSelEnum.getWidth.W)
      def genTable(i: InstPattern) = i.exuOutSel match {
        case e: ExuOutSelEnum.Type => BitPat(e)
        case _ => dc
      }
    },
    new DecodeField[InstPattern, UInt] with CanAutoGenSig {
      def name = "instType"
      def stage = "id"
      def chiselType = UInt(InstTypeEnum.getWidth.W)
      def genTable(i: InstPattern) = i.instType match {
        case e: InstTypeEnum.Type => BitPat(e)
        case _ => dc
      }
    },
    new DecodeField[InstPattern, UInt] with CanAutoGenSig {
      def name = "jumpTargetSel"
      def stage = "wb"
      def chiselType = UInt(JumpTargetSelEnum.getWidth.W)
      def genTable(i: InstPattern) = i.jumpTargetSel match {
        case e: JumpTargetSelEnum.Type => BitPat(e)
        case _ => dc
      }
    },
    new DecodeField[InstPattern, UInt] with CanAutoGenSig {
      def name = "writeBackSel"
      def stage = "wb"
      def chiselType = UInt(WriteBackSelEnum.getWidth.W)
      def genTable(i: InstPattern) = i.writeBackSel match {
        case e: WriteBackSelEnum.Type => BitPat(e)
        case _ => dc
      }
    }
  )
}
