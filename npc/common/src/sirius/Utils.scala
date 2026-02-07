package sirius

import scala.collection.Factory

case class CfgMap[A, T <: Iterable[A]](
  map: Map[(Set[ExtTypeEnum.Type], Set[Int]), T]) {

  def flatten(
    implicit cfg: CoreConfig,
    factory:      Factory[A, T]
  ): T = {
    val items: Iterable[A] = map.filter { case ((exts, xlens), _) =>
      exts.forall(t => cfg.extensions().contains(t)) && xlens.contains(cfg.xlen)
    }.values.flatten

    factory.fromSpecific(items)
  }
}

// case class CfgMap[T <: Iterable[Any]](map: Map[(Set[ExtTypeEnum.Type], Set[Int]), T]) {
//   def flatten(implicit cfg: CoreConfig): T = {
//     map
//       .filter(m =>
//         m._1._1.forall(t => cfg.extensions().contains(t)) && m._1._2
//           .contains(cfg.xlen)
//       )
//       .flatMap(m => m._2)
//   }
// }
