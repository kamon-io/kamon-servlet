package kamon.servlet.utils

import java.time.Instant

import kamon.servlet.server.{RequestServlet, ResponseServlet}

trait RequestContinuation[-Req <: RequestServlet, -Res <: ResponseServlet] {

  def onSuccess(request: Req, response: Res)(end: Instant): Unit
  def onError(request: Req, response: Res)(end: Instant, error: Option[Throwable]): Unit

}
