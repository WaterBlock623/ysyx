package sirius

import chisel3._
import chisel3.util._

object DecoupledState {
  val sIdle :: sBusy :: sWait :: Nil = Enum(3)
}

object DecoupledFsm {
  import DecoupledState._

  def apply(isMaster: Boolean, dio: DecoupledIO[_]): UInt = {
    val defaultState = if (isMaster) { sBusy } else { sWait }
    val state = RegInit(defaultState)
    val toWaitCond = if (isMaster) { dio.valid } else { dio.ready }
    val toBusyCond = if (isMaster) { dio.ready } else { dio.valid }
    state := MuxLookup(state, defaultState)(
      Seq(
        sBusy -> Mux(toWaitCond, sWait, sBusy),
        sWait -> Mux(toBusyCond, sBusy, sWait)
      )
    )
    state
  }
}

object DecoupledMasterSlaveFsm {
  def apply(masterIO: DecoupledIO[_], slaveIO: DecoupledIO[_]): (UInt, UInt) = {
    val masterState = DecoupledFsm(true, masterIO)
    val slaveState = DecoupledFsm(false, slaveIO)
    (masterState, slaveState)
  }
}
