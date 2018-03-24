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

package kamon.servlet.server

import java.time.Instant
import java.util.Locale

import kamon.Kamon
import kamon.context.{Context, Storage, TextMap}
import kamon.servlet.Continuation
import kamon.servlet.utils.RequestContinuation
import kamon.trace.Span

object ServletTracing {

  /**
    * Create a new Span for representing the incoming request and propagate the Kamon context by joining
    * to the incoming context or creating new one in other case.
    *
    * <p>It's written with continuation-passing style (CPS) to keep explicitly the order of metrics and trace managing.
    *
    * <p>The continuation function must be called with the {@link TracingContinuation} to continue the request processing.
    *
    * <p>{@link TracingContinuation} object has the callbacks for success or fail response processing.
    *
    * @param request: Request
    * @param response: Response
    * @param continuation: continuation function where it will be passed the final trace processing
    * @tparam Hole: Value Type to be used to manage tracing on the resulted response
    * @tparam Result: Final Result Type produce after computation of continuation provided
    * @return
    */
  def withTracing[Hole <: TracingContinuation, Result](request: RequestServlet, response: ResponseServlet)
                                                      (continuation: Continuation[TracingContinuation, Result]): Result = {

    val incomingContext = decodeContext(request)
    val serverSpan = createSpan(incomingContext, request)

    val scope = Kamon.storeContext(incomingContext.withKey(Span.ContextKey, serverSpan))
    continuation(TracingContinuation(request, response, scope, serverSpan))
  }

  private def createSpan(incomingContext: Context, request: RequestServlet): Span = {
    val operationName = kamon.servlet.Servlet.generateOperationName(request)
    Kamon.buildSpan(operationName)
      .asChildOf(incomingContext.get(Span.ContextKey))
      .withMetricTag("span.kind", "server")
      .withMetricTag("component", "servlet.server")
      .withTag("http.method", request.getMethod.toUpperCase(Locale.ENGLISH))
      .withTag("http.url", request.uri)
      .start()
  }


  private def decodeContext(request: RequestServlet): Context = {
    val headersTextMap = readOnlyTextMapFromHeaders(request)
    Kamon.contextCodec().HttpHeaders.decode(headersTextMap)
  }

  private def readOnlyTextMapFromHeaders(request: RequestServlet): TextMap = new TextMap {
    override def values: Iterator[(String, String)] = request.headers.iterator
    override def get(key: String): Option[String] = request.headers.get(key)
    override def put(key: String, value: String): Unit = {}
  }
}

case class TracingContinuation(request: RequestServlet, response: ResponseServlet, scope: Storage.Scope,
                               serverSpan: Span) extends RequestContinuation {

  import TracingContinuation._

  def onSuccess(end: Instant): Unit = {
    always(end)
    finishSpan(serverSpan, end)
  }

  def onError(end: Instant, error: Option[Throwable]): Unit = {
    always(end)
    finishSpanWithError(serverSpan, end, error)
  }

  private def always(end: Instant): Unit = {
    scope.close()
    handleStatusCode(serverSpan, response.status)
    serverSpan.tag("http.status_code", response.status)
  }

  private def handleStatusCode(span: Span, code: Int): Unit =
    if (code >= 500) span.addError("error")
    else if (code == 404) span.setOperationName("not-found")

  private def finishSpan(serverSpan: Span, endTimestamp: Instant): Unit = {
    serverSpan.finish(endTimestamp)
  }

  private def finishSpanWithError(serverSpan: Span, endTimestamp: Instant, error: Option[Throwable]): Unit = {
    error match {
      case Some(e) => serverSpan.addError(errorMessage, e)
      case None    => serverSpan.addError(errorMessage)
    }
    finishSpan(serverSpan, endTimestamp)
  }
}

object TracingContinuation {
  val errorMessage = "abnormal termination"
}
