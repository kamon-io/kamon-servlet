/*
 * =========================================================================================
 * Copyright © 2013-2020 the kamon project <http://kamon.io/>
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

package kamon.servlet.v25.server

import java.time.Instant

import javax.servlet.FilterChain
import kamon.Kamon
import kamon.servlet.server.FilterDelegation
import kamon.servlet.utils.RequestContinuation
import kamon.servlet.v25.KamonFilterV25Config

import scala.util.Try

class FilterDelegationV25(val underlineChain: FilterChain)
  extends FilterDelegation[RequestServletV25, ResponseServletV25, ResponseProcessingContinuation] {

  override def chain(request: RequestServletV25, response: ResponseServletV25)
                    (continuation: ResponseProcessingContinuation): Try[Unit] = {
    val result = Try(underlineChain.doFilter(request.underlineRequest, response.underlineResponse))
    handle(request, response)(result, continuation)
  }

  protected def handle(request: RequestServletV25, response: ResponseServletV25)
                      (result: Try[Unit], continuation: ResponseProcessingContinuation): Try[Unit] = {
    result
      .map { value =>
        continuation.onSuccess(request, response)(Kamon.clock().instant())
        value
      }
      .recover {
        case error: Throwable =>
          continuation.onError(request, response)(Kamon.clock().instant(), Some(error))
          error
      }
  }

  override def joinContinuations(continuations: RequestContinuation[RequestServletV25, ResponseServletV25]*): ResponseProcessingContinuation = ResponseProcessingContinuation(continuations: _*)
}

object FilterDelegationV25 {
  def apply(underlineChain: FilterChain): FilterDelegationV25 = new FilterDelegationV25(underlineChain)
}

case class ResponseProcessingContinuation(continuations: RequestContinuation[RequestServletV25, ResponseServletV25]*) extends RequestContinuation[RequestServletV25, ResponseServletV25] {

  type Request = RequestServletV25
  type Response = ResponseServletV25

  override def onSuccess(request: Request, response: Response)(end: Instant): Unit = continuations.foreach(_.onSuccess(request, response)(end))

  override def onError(request: Request, response: Response)(end: Instant, error: Option[Throwable]): Unit = {
    val resp = KamonFilterV25Config.errorResponseHandler.withRightStatus(response)
    continuations.foreach(_.onError(request, resp)(end, error))
  }
}
