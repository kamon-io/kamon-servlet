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

package kamon.servlet.v25.server

import java.time.Instant

import javax.servlet.FilterChain
import kamon.Kamon
import kamon.servlet.server.FilterDelegation
import kamon.servlet.utils.RequestContinuation

import scala.util.Try

case class FilterDelegationV25(underlineChain: FilterChain)
  extends FilterDelegation[RequestServletV25, ResponseServletV25, ResponseProcessingContinuation] {

  override def chain(request: RequestServletV25, response: ResponseServletV25)
                    (continuation: ResponseProcessingContinuation): Try[Unit] = {
    val result = Try(underlineChain.doFilter(request.underlineRequest, response.underlineResponse))
    handle(request, response)(result, continuation)
  }

  private def handle(request: RequestServletV25, response: ResponseServletV25)
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

case class ResponseProcessingContinuation(continuations: RequestContinuation*) extends RequestContinuation {
  override def onSuccess(end: Instant): Unit = continuations.foreach(_.onSuccess(end))
  override def onError(end: Instant, error: Option[Throwable]): Unit = continuations.foreach(_.onError(end, error))
}
