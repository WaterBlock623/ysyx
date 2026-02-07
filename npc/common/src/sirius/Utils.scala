package sirius

case class CfgMap[T](cfg: CoreConfig, map: Map[(Set[ExtTypeEnum.Type], Set[Int]), Seq[T]]) {
  def toSeq: Seq[T] = {
    map
      .filter(m =>
        m._1._1.forall(t => cfg.extensions.contains(t)) && m._1._2
          .contains(cfg.xlen)
      )
      .flatMap(m => m._2)
      .toSeq
  }
}
