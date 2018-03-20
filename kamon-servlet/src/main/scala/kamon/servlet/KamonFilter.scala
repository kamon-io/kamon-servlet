/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.servlet

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import kamon.Kamon
import kamon.servlet.Metrics.{GeneralMetrics, RequestTimeMetrics, ResponseTimeMetrics, ServiceMetrics}
import kamon.servlet.server._

import scala.language.postfixOps
import scala.util.Try

class KamonFilter extends Filter {

  val servletMetrics = ServletMetrics(ServiceMetrics(GeneralMetrics(), RequestTimeMetrics(), ResponseTimeMetrics()))

  override def init(filterConfig: FilterConfig): Unit = ()

  override def destroy(): Unit = ()

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val req = request.asInstanceOf[HttpServletRequest]
    val res = response.asInstanceOf[HttpServletResponse]

    val start = Kamon.clock().instant()

    servletMetrics.withMetrics(start, req, res) { metricsContinuation =>
      ServletTracing.withTracing(req, res, metricsContinuation) { tracingContinuation =>
        process(req, res, tracingContinuation) {
          chain.doFilter(request, response)
        }
      }
    } get

  }

  private def process(request: HttpServletRequest, response: HttpServletResponse,
                      tracingContinuation: TracingContinuation)(thunk: => Unit): Try[Unit] = {
    val result = Try(thunk)
    onFinish(request, response)(result, tracingContinuation)
  }

  private def onFinish(request: HttpServletRequest, response: HttpServletResponse): (Try[Unit], TracingContinuation) => Try[Unit] = {
    if (request.isAsyncStarted) handleAsync(request, response)
    else                        handleSync(request, response)
  }

  private def handleAsync(request: HttpServletRequest, response: HttpServletResponse)
                         (result: Try[Unit], continuation: TracingContinuation): Try[Unit] = {
    val handler = FromTracingResponseHandler(continuation)
    request.getAsyncContext.addListener(KamonAsyncListener(handler))
    result
  }

  private def handleSync(request: HttpServletRequest, response: HttpServletResponse)
                        (result: Try[Unit], continuation: TracingContinuation): Try[Unit] = {
    val handler = FromTracingResponseHandler(continuation)
    result
      .map { value =>
        handler.onComplete()
        value
      }
      .recover {
        case error: Throwable =>
          handler.onError()
          error
      }
  }

}

case class FromTracingResponseHandler(continuation: TracingContinuation) extends KamonResponseHandler {
  override def onError(): Unit = continuation.onError(Kamon.clock().instant())
  override def onComplete(): Unit = continuation.onSuccess(Kamon.clock().instant())
  override def onStartAsync(): Unit = ()
  override def onTimeout(): Unit = this.onError()
}
