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

import java.time.Instant

import javax.servlet._
import kamon.Kamon
import kamon.servlet.server.FilterDelegation
import kamon.servlet.utils.RequestContinuation

import scala.util.Try


case class FilterDelegationV3(underlineChain: FilterChain)
  extends FilterDelegation[RequestServletV3, ResponseServletV3, ResponseProcessingContinuation] {

  override def chain(request: RequestServletV3, response: ResponseServletV3)
                    (continuation: ResponseProcessingContinuation): Try[Unit] = {
    val result = Try(underlineChain.doFilter(request.underlineRequest, response.underlineResponse))
    onFinish(request, response)(result, continuation)
  }

  private def onFinish(request: RequestServletV3, response: ResponseServletV3): (Try[Unit], ResponseProcessingContinuation) => Try[Unit] = {
    if (request.isAsync) handleAsync(request, response)
    else                 handleSync(request, response)
  }

  private def handleAsync(request: RequestServletV3, response: ResponseServletV3)
                         (result: Try[Unit], continuation: ResponseProcessingContinuation): Try[Unit] = {
    request.addListener(KamonAsyncListener(continuation))
    result
  }

  private def handleSync(request: RequestServletV3, response: ResponseServletV3)
                        (result: Try[Unit], continuation: ResponseProcessingContinuation): Try[Unit] = {
    result
      .map { value =>
        continuation.onSuccess(Kamon.clock().instant())
        value
      }
      .recover {
        case error: Throwable =>
          continuation.onError(Kamon.clock().instant(), Some(error))
          error
      }
  }

  override def fromUppers(continuations: RequestContinuation*): ResponseProcessingContinuation = ResponseProcessingContinuation(continuations: _*)
}

final case class KamonAsyncListener(handler: ResponseProcessingContinuation) extends AsyncListener {
  override def onError(event: AsyncEvent): Unit = handler.onError(Kamon.clock().instant(), Option(event.getThrowable))
  override def onComplete(event: AsyncEvent): Unit = handler.onSuccess(Kamon.clock().instant())
  override def onStartAsync(event: AsyncEvent): Unit = ()
  override def onTimeout(event: AsyncEvent): Unit = handler.onError(Kamon.clock().instant(), Option(event.getThrowable))
}

case class ResponseProcessingContinuation(continuations: RequestContinuation*) extends RequestContinuation {
  override def onSuccess(end: Instant): Unit = continuations.foreach(_.onSuccess(end))
  override def onError(end: Instant, error: Option[Throwable]): Unit = continuations.foreach(_.onError(end, error))
}
