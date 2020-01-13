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

import kamon.Kamon
import kamon.context.Context
import kamon.instrumentation.http.HttpServerInstrumentation
import kamon.instrumentation.http.HttpServerInstrumentation.RequestHandler
import kamon.servlet.Continuation
import kamon.servlet.utils.RequestContinuation
import kamon.trace.Span

case class ServletTracing(instrumentation: HttpServerInstrumentation) {

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
    * @param request      : Request
    * @param response     : Response
    * @param continuation : continuation function where it will be passed the final trace processing
    * @tparam Hole   : Value Type to be used to manage tracing on the resulted response
    * @tparam Result : Final Result Type produce after computation of continuation provided
    * @return
    */
  def withTracing[Hole <: TracingContinuation, Result](request: RequestServlet, response: ResponseServlet)
                                                      (continuation: Continuation[TracingContinuation, Result]): Result = {

    //    val incomingContext = decodeContext(request)
    //    val serverSpan = createSpan(incomingContext, request)
    val requestHandler: RequestHandler = instrumentation.createHandler(request)
    requestHandler.requestReceived()
    Kamon.runWithContext(requestHandler.context /* incomingContext.withKey(Span.ContextKey, serverSpan)*/) {
      continuation(TracingContinuation(Kamon.currentContext(), requestHandler, instrumentation.settings))
    }
  }

  //  private def createSpan(incomingContext: Context, request: RequestServlet): Span = {
  //    val operationName = kamon.servlet.Servlet.generateOperationName(request)
  //    Kamon.buildSpan(operationName)
  //      .asChildOf(incomingContext.get(Span.ContextKey))
  //      .withMetricTag("span.kind", "server")
  //      .withMetricTag("component", kamon.servlet.Servlet.tags.serverComponent)
  //      .withTag("http.method", request.getMethod.toUpperCase(Locale.ENGLISH))
  //      .withTag("http.url", request.uri)
  //      .start()
  //  }
  //
  //
  //  private def decodeContext(request: RequestServlet): Context = {
  //    val headersTextMap = readOnlyTextMapFromHeaders(request)
  //    Kamon.contextCodec().HttpHeaders.decode(headersTextMap)
  //  }
  //
  //  private def readOnlyTextMapFromHeaders(request: RequestServlet): TextMap = new TextMap {
  //    override def values: Iterator[(String, String)] = request.headers.iterator
  //    override def get(key: String): Option[String] = request.headers.get(key)
  //    override def put(key: String, value: String): Unit = {}
  //  }
}

case class TracingContinuation(scope: Context, requestHandler: RequestHandler,
                               settings: HttpServerInstrumentation.Settings) extends RequestContinuation[RequestServlet, ResponseServlet] {

  import TracingContinuation._

  type Request = RequestServlet
  type Response = ResponseServlet

  val serverSpan: Span = requestHandler.span

  def onSuccess(request: Request, response: Response)(end: Instant): Unit = {
    always(response, end)
    finishSpan(serverSpan, end)
  }

  def onError(request: Request, response: Response)(end: Instant, error: Option[Throwable]): Unit = {
    always(response, end)
    finishSpanWithError(serverSpan, end, error)
  }

  private def always(response: Response, end: Instant): Unit = {
    requestHandler.responseSent()
    handleStatusCode(serverSpan, response.statusCode)
    serverSpan.tag("http.status_code", response.statusCode)
  }

  private def handleStatusCode(span: Span, code: Int): Unit =
    if (code >= 500) span.fail("error")
    else if (code == 404) span.name(settings.unhandledOperationName)

  private def finishSpan(serverSpan: Span, endTimestamp: Instant): Unit = {
    serverSpan.finish(endTimestamp)
  }

  private def finishSpanWithError(serverSpan: Span, endTimestamp: Instant, error: Option[Throwable]): Unit = {
    error match {
      case Some(e) => serverSpan.fail(errorMessage, e)
      case None => serverSpan.fail(errorMessage)
    }
    finishSpan(serverSpan, endTimestamp)
  }
}

object TracingContinuation {
  val errorMessage = "abnormal termination"
}
