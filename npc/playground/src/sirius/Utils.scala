package sirius

import chisel3._
import chisel3.util._

object SignExt {
  def apply(data: UInt, len: Int) = {
    val dataLen = data.getWidth
    val signBit = data(dataLen - 1)
    if (dataLen >= len) data(len - 1, 0) else Fill(len - dataLen, signBit) ## data
  }
}

object ZeroExt {
  def apply(data: UInt, len: Int) = {
    val dataLen = data.getWidth
    if (dataLen >= len) data(len - 1, 0) else 0.U((len - dataLen).W) ## data
  }
}
