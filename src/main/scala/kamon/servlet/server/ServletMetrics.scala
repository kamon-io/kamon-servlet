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
import java.time.temporal.ChronoUnit
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import kamon.Kamon
import kamon.metric.{Histogram, RangeSampler}
import kamon.servlet.Metrics.{GeneralMetrics, RequestTimeMetrics, ResponseTimeMetrics, ServiceMetrics}
import kamon.servlet.utils.OnlyOnce

import scala.util.Try

object ServletMetrics {

  def withMetrics(start: Instant, request: HttpServletRequest, response: HttpServletResponse)
                 (continuation: MetricsContinuation => Try[Unit]): Try[Unit] = {

    val serviceMetrics = ServiceMetrics(GeneralMetrics(), RequestTimeMetrics(), ResponseTimeMetrics())
    serviceMetrics.generalMetrics.activeRequests.increment()

    continuation(MetricsContinuation(request, response, start, serviceMetrics))
  }
}

/**
  * continuation-passing style (CPS) to keep explicitly the order of metrics and tracing managing avoiding spaghetti code
  * @param request
  * @param response
  * @param start
  * @param serviceMetrics
  */
case class MetricsContinuation(request: HttpServletRequest, response: HttpServletResponse,
                               start: Instant, serviceMetrics: ServiceMetrics) {

  def apply(result: Try[Unit])(end: Instant): Try[Unit] = {
    onFinish(request, response)(start, serviceMetrics, result)
  }

  private def onFinish(request: HttpServletRequest, response: HttpServletResponse): (Instant, ServiceMetrics, Try[Unit]) => Try[Unit] = {
    if (request.isAsyncStarted) handleAsync(request, response)
    else                        handleSync(request, response)
  }

  private def handleAsync(request: HttpServletRequest, response: HttpServletResponse)
                         (timeStart: Instant, serviceMetrics: ServiceMetrics, result: Try[Unit]): Try[Unit] = {
    val handler = MetricsResponseHandler(timeStart, serviceMetrics, request, response)
    request.getAsyncContext.addListener(KamonAsyncListener(handler))
    result
  }

  private def handleSync(request: HttpServletRequest, response: HttpServletResponse)
                        (timeStart: Instant, serviceMetrics: ServiceMetrics, result: Try[Unit]): Try[Unit] = {
    val handler = MetricsResponseHandler(timeStart, serviceMetrics, request, response)
    result
      .map { x => handler.onComplete(); x }
      .recover {
        case exc: Throwable =>
          handler.onError()
          exc
      }
  }
}

case class MetricsResponseHandler(timeStart: Instant, serviceMetrics: ServiceMetrics, request: HttpServletRequest, response: HttpServletResponse)
  extends KamonResponseHandler with OnlyOnce {

  override def onError(): Unit = onlyOnce {
    val elapsed = calculateElapsed
    always(elapsed)
    incrementCounts(serviceMetrics.generalMetrics.serviceErrors, elapsed)
  }

  override def onComplete(): Unit = onlyOnce {
    val elapsed = calculateElapsed
    always(elapsed)
    incrementCounts(serviceMetrics.generalMetrics.headersTimes, elapsed)
    responseMetrics(serviceMetrics.responseTimeMetrics, response.getStatus, elapsed)
  }

  override def onStartAsync(): Unit = ()

  override def onTimeout(): Unit = onlyOnce {
    onError()
  }

  private def calculateElapsed: Long = timeStart.until(Kamon.clock().instant(), ChronoUnit.NANOS)

  private def always(elapsed: Long): Unit = {
    requestMetrics(serviceMetrics.requestTimeMetrics, serviceMetrics.generalMetrics.activeRequests)(request.getMethod, elapsed)
  }

  private def responseTime(responseTime: ResponseTimeMetrics, status: Int): Histogram =
    status match {
      case hundreds      if hundreds      < 200 => responseTime.forStatusCode("1xx")
      case twoHundreds   if twoHundreds   < 300 => responseTime.forStatusCode("2xx")
      case threeHundreds if threeHundreds < 400 => responseTime.forStatusCode("3xx")
      case fourHundreds  if fourHundreds  < 500 => responseTime.forStatusCode("4xx")
      case _                                    => responseTime.forStatusCode("5xx")
    }

  private def responseMetrics(responseTimers: ResponseTimeMetrics, status: Int, elapsed: Long): Unit =
    incrementCounts(responseTime(responseTimers, status), elapsed)

  private def incrementCounts(histogram: Histogram, elapsed: Long): Unit = histogram.record(elapsed)

  private def requestTime(rt: RequestTimeMetrics, method: String) = {
    rt.forMethod(method.toLowerCase())
  }

  private def requestMetrics(rt: RequestTimeMetrics, activeRequests: RangeSampler)
                            (method: String, elapsed: Long): Unit = {
    val timer = requestTime(rt, method)
    incrementCounts(timer, elapsed)
    incrementCounts(rt.forMethod("total"), elapsed)
    activeRequests.decrement()
  }
}
