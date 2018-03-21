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
import kamon.context.{Context, TextMap}
import kamon.trace.Span

import scala.util.Try

object ServletTracing {

  def withTracing(request: RequestServlet, response: ResponseServlet, metricsContinuation: MetricsContinuation)
                 (continuation: TracingContinuation => Try[Unit]): Try[Unit] = {

    val incomingContext = decodeContext(request)
    val serverSpan = createSpan(incomingContext, request)

    Kamon.withContext(incomingContext.withKey(Span.ContextKey, serverSpan)) {
      continuation(TracingContinuation(request, response, serverSpan, metricsContinuation))
    }
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

case class TracingContinuation(request: RequestServlet, response: ResponseServlet,
                               serverSpan: Span, continuation: MetricsContinuation) {

  import TracingContinuation._

  def onSuccess(end: Instant): Unit = {
    continuation.onSuccess(end)
    always(end)
    serverSpan.finish(Kamon.clock().instant())
  }

  def onError(end: Instant, error: Option[Throwable]): Unit = {
    always(end)
    continuation.onError(end)
    finishSpanWithError(serverSpan, Kamon.clock().instant(), error)
  }

  private def always(end: Instant): Unit = {
    handleStatusCode(serverSpan, response.status)
    serverSpan.tag("http.status_code", response.status)
  }

  private def handleStatusCode(span: Span, code: Int): Unit =
    if (code >= 500) span.addError("error")
    else if (code == 404) span.setOperationName("not-found")

  private def finishSpanWithError(serverSpan: Span, endTimestamp: Instant, error: Option[Throwable]): Unit = {
    error match {
      case Some(e) => serverSpan.addError(errorMessage, e)
      case None    => serverSpan.addError(errorMessage)
    }
    serverSpan.finish(endTimestamp)
  }
}

object TracingContinuation {
  val errorMessage = "abnormal termination"
}
