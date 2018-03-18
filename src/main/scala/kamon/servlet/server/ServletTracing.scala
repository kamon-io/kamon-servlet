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
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import kamon.Kamon
import kamon.context.{Context, TextMap}
import kamon.servlet.utils.OnlyOnce
import kamon.trace.Span

import scala.util.Try

object ServletTracing {

  def withTracing(request: HttpServletRequest, response: HttpServletResponse)(thunk: => Unit): Unit = {

    val serverSpan = createSpan(request)
    val scope = Kamon.storeContext(Context.create(Span.ContextKey, serverSpan))

    val result = Try(thunk)

    scope.close()

    onFinish(request, response)(serverSpan, result).get
  }

  private def onFinish(request: HttpServletRequest, response: HttpServletResponse): (Span, Try[Unit]) => Try[Unit] = {
    if (request.isAsyncStarted) handleAsync(request, response)
    else                        handleSync(request, response)
  }

  private def handleAsync(request: HttpServletRequest, response: HttpServletResponse)
                         (serverSpan: Span, result: Try[Unit]): Try[Unit] = {
    val handler = TracingResponseHandler(serverSpan, request, response)
    request.getAsyncContext.addListener(KamonAsyncListener(handler))
    result
  }

  private def handleSync(request: HttpServletRequest, response: HttpServletResponse)
                         (serverSpan: Span, result: Try[Unit]): Try[Unit] = {
    val handler = TracingResponseHandler(serverSpan, request, response)
    result
      .map { x => handler.onComplete(); x }
      .recover {
        case exc: Throwable =>
          handler.onError()
          exc
      }
  }

  private def createSpan(request: HttpServletRequest): Span = {
    val incomingContext = decodeContext(request)
    val operationName = kamon.servlet.KamonServletSupport.generateOperationName(request)
    Kamon.buildSpan(operationName)
      .asChildOf(incomingContext.get(Span.ContextKey))
      .withMetricTag("span.kind", "server")
      .withMetricTag("component", "servlet.server")
      .withTag("http.method", request.getMethod.toUpperCase(Locale.ENGLISH))
      .withTag("http.url", request.getRequestURI)
      .start()
  }


  private def decodeContext(request: HttpServletRequest): Context = {
    val headersTextMap = readOnlyTextMapFromHeaders(request)
    Kamon.contextCodec().HttpHeaders.decode(headersTextMap)
  }

  // FIXME: improve this implementation
  private def readOnlyTextMapFromHeaders(request: HttpServletRequest): TextMap = new TextMap {
    private val headersMap: Map[String, String] = {
      val headersIterator = request.getHeaderNames
      val headers = Map.newBuilder[String, String]
      while (headersIterator.hasMoreElements) {
        val name = headersIterator.nextElement()
        headers += (name -> request.getHeader(name))
      }
      headers.result()
    }

    override def values: Iterator[(String, String)] = headersMap.iterator
    override def get(key: String): Option[String] = headersMap.get(key)
    override def put(key: String, value: String): Unit = {}
  }
}

final case class TracingResponseHandler(serverSpan: Span, request: HttpServletRequest, response: HttpServletResponse)
  extends KamonResponseHandler with OnlyOnce {

  override def onError(): Unit = onlyOnce {
    always()
    finishSpanWithError(serverSpan, Kamon.clock().instant())
  }

  override def onComplete(): Unit = onlyOnce {
    always()
    serverSpan.finish(Kamon.clock().instant())
  }

  override def onStartAsync(): Unit = ()

  override def onTimeout(): Unit = onlyOnce {
    always()
    finishSpanWithError(serverSpan, Kamon.clock().instant())
  }

  private def always(): Unit = {
    handleStatusCode(serverSpan, response.getStatus)
    serverSpan.tag("http.status_code", response.getStatus)
  }

  private def handleStatusCode(span: Span, code: Int): Unit =
    if (code >= 500) span.addError("error")
    else if (code == 404) span.setOperationName("not-found")

  private def finishSpanWithError(serverSpan: Span, endTimestamp: Instant): Unit = {
    serverSpan.addError("abnormal termination")
    serverSpan.finish(endTimestamp)
  }
}
