package kamon

package object servlet {

  type Continuation[Hole, Result] = Hole => Result

}
