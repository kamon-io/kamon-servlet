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

import kamon.Kamon
import kamon.metric.{Histogram, RangeSampler}
import kamon.servlet.Continuation
import kamon.servlet.Metrics.{GeneralMetrics, RequestTimeMetrics, ResponseTimeMetrics, ServiceMetrics}
import kamon.servlet.utils.{OnlyOnce, RequestContinuation}


object ServletMetrics {

  def apply(): ServletMetrics = {
    if (isEnabled) DefaultServletMetrics(ServiceMetrics(GeneralMetrics(), RequestTimeMetrics(), ResponseTimeMetrics()))
    else NoOpServletMetrics
  }

  private def isEnabled = Kamon.config.getBoolean("kamon.servlet.metrics.enabled")
}

trait ServletMetrics {

  /**
    *
    * <P>Processing metrics around the request.
    *
    * <p>It's written with continuation-passing style (CPS) to keep explicitly the order of metrics and trace managing.
    *
    * <p>The continuation function must be called with the {@link MetricsContinuation} to continue the request processing.
    *
    * <p>{@link MetricsContinuation} object has the callbacks for success or fail response processing.
    *
    * @param start: instant since request start to process
    * @param request; request in process
    * @param response: response in process
    * @param continuation: continuation function where it will be passed the final metrics processing
    * @tparam Result: Final Result Type produce after computation of continuation provided
    * @return unit with possible exception caught
    */
  def withMetrics[Result](start: Instant, request: RequestServlet, response: ResponseServlet)
                         (continuation: Continuation[MetricsContinuation, Result]): Result
}

case class DefaultServletMetrics(serviceMetrics: ServiceMetrics) extends ServletMetrics {

  override def withMetrics[Result](start: Instant, request: RequestServlet, response: ResponseServlet)
                                  (continuation: Continuation[MetricsContinuation, Result]): Result = {

    val serviceMetrics = ServiceMetrics(GeneralMetrics(), RequestTimeMetrics(), ResponseTimeMetrics())
    serviceMetrics.generalMetrics.activeRequests.increment()

    continuation(DefaultMetricsContinuation(request, response, start, serviceMetrics))
  }
}

case object NoOpServletMetrics extends ServletMetrics {

  override def withMetrics[Result](start: Instant, request: RequestServlet, response: ResponseServlet)
                                  (continuation: Continuation[MetricsContinuation, Result]): Result = {
    continuation(NoOpMetricsContinuation)
  }
}

/**
  * It's passed to the continuation function to generate metrics after request processing
  */
trait MetricsContinuation extends RequestContinuation {
  def onSuccess(end: Instant): Unit
  def onError(end: Instant, error: Option[Throwable]): Unit
}

/**
  * Default implementation of MetricsContinuation
  * @param request: request in process
  * @param response: response in process
  * @param start: instant since request start to process
  * @param serviceMetrics: metrics generator
  */
case class DefaultMetricsContinuation(request: RequestServlet, response: ResponseServlet,
                                      start: Instant, serviceMetrics: ServiceMetrics) extends MetricsContinuation {


  def onSuccess(end: Instant): Unit = {
    val handler = MetricsResponseHandler(request, response, start, end, serviceMetrics)
    handler.onComplete()
  }

  def onError(end: Instant, error: Option[Throwable]): Unit = {
    val handler = MetricsResponseHandler(request, response, start, end, serviceMetrics)
    handler.onError()
  }
}

case object NoOpMetricsContinuation extends MetricsContinuation {
  override def onSuccess(end: Instant): Unit = ()
  override def onError(end: Instant, error: Option[Throwable]): Unit = ()
}

case class MetricsResponseHandler(request: RequestServlet, response: ResponseServlet,
                                  start: Instant, end: Instant, serviceMetrics: ServiceMetrics)
  extends OnlyOnce {

  private val elapsed = start.until(end, ChronoUnit.NANOS)

  def onError(): Unit = onlyOnce {
    always()
    incrementCounts(serviceMetrics.generalMetrics.serviceErrors, elapsed)
  }

  def onComplete(): Unit = onlyOnce {
    always()
    incrementCounts(serviceMetrics.generalMetrics.headersTimes, elapsed)
    responseMetrics(serviceMetrics.responseTimeMetrics, response.status, elapsed)
  }

  private def always(): Unit = {
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
