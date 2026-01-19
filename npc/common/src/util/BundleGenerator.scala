package util

import chisel3._

trait CanAutoGenSig {
  def stage: String
  def chiselType: Data
}
