package kamon.servlet.utils

import scala.collection.immutable.TreeMap

object MapUtils {

  def caseInsensitiveMap[A](map: Map[String, A]): Map[String, A] = {
    TreeMap(map.toList: _*)(Ordering.comparatorToOrdering(String.CASE_INSENSITIVE_ORDER))
  }

}
