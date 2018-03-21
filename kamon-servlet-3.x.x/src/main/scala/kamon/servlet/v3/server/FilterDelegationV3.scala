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

package kamon.servlet.v3.server

import javax.servlet._
import kamon.Kamon
import kamon.servlet.server.{FilterDelegation, KamonResponseHandler, TracingContinuation}

import scala.util.Try


case class FilterDelegationV3(underlineChain: FilterChain) extends FilterDelegation[RequestServletV3, ResponseServletV3] {

  override def chain(request: RequestServletV3, response: ResponseServletV3)(tracingContinuation: TracingContinuation): Try[Unit] = {
    val result = Try(underlineChain.doFilter(request.underlineRequest, response.underlineResponse))
    onFinish(request, response)(result, tracingContinuation)
  }


  private def onFinish(request: RequestServletV3, response: ResponseServletV3): (Try[Unit], TracingContinuation) => Try[Unit] = {
    if (request.isAsync) handleAsync(request, response)
    else                        handleSync(request, response)
  }

  private def handleAsync(request: RequestServletV3, response: ResponseServletV3)
                         (result: Try[Unit], continuation: TracingContinuation): Try[Unit] = {
    val handler = FromTracingResponseHandler(continuation)
    request.addListener(handler)
    result
  }

  private def handleSync(request: RequestServletV3, response: ResponseServletV3)
                        (result: Try[Unit], continuation: TracingContinuation): Try[Unit] = {
    val handler = FromTracingResponseHandler(continuation)
    result
      .map { value =>
        handler.onComplete()
        value
      }
      .recover {
        case error: Throwable =>
          handler.onError(Some(error))
          error
      }
  }
}

final case class KamonAsyncListener(handler: KamonResponseHandler) extends AsyncListener {
  override def onError(event: AsyncEvent): Unit = handler.onError(Option(event.getThrowable))
  override def onComplete(event: AsyncEvent): Unit = handler.onComplete()
  override def onStartAsync(event: AsyncEvent): Unit = handler.onStartAsync()
  override def onTimeout(event: AsyncEvent): Unit = handler.onError(Option(event.getThrowable))
}

case class FromTracingResponseHandler(continuation: TracingContinuation) extends KamonResponseHandler {
  override def onError(error: Option[Throwable]): Unit = continuation.onError(Kamon.clock().instant(), error) // FIXME: save Throwable
  override def onComplete(): Unit = continuation.onSuccess(Kamon.clock().instant())
  override def onStartAsync(): Unit = ()
}
