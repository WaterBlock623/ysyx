package sirius

import scala.collection.IterableOps

case class CfgMap[A, CC[_]](map: Map[(Set[ExtTypeEnum.Type], Set[Int]), IterableOps[A, CC, CC[A]]]) {
  def flatten(implicit cfg: CoreConfig): CC[A] = {
    val filtered = map
      .filter { case ((exts, xlens), _) =>
        exts.forall(t => cfg.extensions.contains(t)) && xlens.contains(cfg.xlen)
      }
      .values
    if (filtered.isEmpty) {
      throw new NoSuchElementException("Nothing is matched")
    }
    filtered.head.iterableFactory.from(filtered.flatten)
  }
}

// case class CfgMap[T <: Iterable[Any]](map: Map[(Set[ExtTypeEnum.Type], Set[Int]), T]) {
//   def flatten(implicit cfg: CoreConfig): T = {
//     map
//       .filter(m =>
//         m._1._1.forall(t => cfg.extensions.contains(t)) && m._1._2
//           .contains(cfg.xlen)
//       )
//       .flatMap(m => m._2)
//   }
// }
