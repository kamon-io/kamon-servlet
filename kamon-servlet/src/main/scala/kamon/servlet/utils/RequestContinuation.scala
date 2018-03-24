package kamon.servlet.utils

import java.time.Instant

trait RequestContinuation {

  def onSuccess(end: Instant): Unit
  def onError(end: Instant, error: Option[Throwable]): Unit

}
