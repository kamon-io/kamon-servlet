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

package kamon.servlet.v3

import java.util.concurrent.Executors

import javax.servlet.http.HttpServlet
import kamon.instrumentation.http.HttpServerMetrics
import kamon.servlet.Servlet
import kamon.servlet.v3.client.HttpClientSupport
import kamon.servlet.v3.server.{AsyncTestServlet, JettySupport}
import kamon.testkit.{InstrumentInspection, TestSpanReporter}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.concurrent.{ExecutionContext, Future}


class AsyncHttpMetricsSpec extends WordSpec
  with Matchers
  with Eventually
  with SpanSugar
  with InstrumentInspection.Syntax
  with OptionValues
  with TestSpanReporter
  with BeforeAndAfterAll
  with JettySupport
  with HttpClientSupport {

  override val servlet: HttpServlet = AsyncTestServlet(defaultDuration = 10)(durationSlowly = 1000)

  override protected def beforeAll(): Unit = {
    startServer()
    applyConfig(
      s"""
         |kamon {
         |  instrumentation.servlet.server.interface = "$host"
         |  instrumentation.servlet.server.port = $port
         |}
         |
    """.stripMargin
    )
  }

  override protected def afterAll(): Unit = {
    stopServer()
  }

  private val parallelRequestExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(15))

  def serverInstruments(): HttpServerMetrics.HttpServerInstruments = HttpServerMetrics.of(Servlet.tags.serverComponent, Servlet.server.interface, Servlet.server.port)

  "The Http Metrics generation on Async Servlet 3.x.x" should {
    "track the total of active requests" in {
      for (_ <- 1 to 10) yield {
        Future {
          get("/async/tracing/slowly")
        }(parallelRequestExecutor)
      }

      eventually(timeout(5 seconds)) {
        serverInstruments().activeRequests.distribution().max should (be > 0L and be <= 10L)
      }

      eventually(timeout(5 seconds)) {
        serverInstruments().activeRequests.distribution().min should (be > 0L and be <= 10L)
      }
      testSpanReporter().clear()
    }

    "track the number of responses with status code 2xx" in {
      for (_ <- 1 to 100) yield get("/async/tracing/ok")
      serverInstruments().requestsSuccessful.value(resetState = false) should be > 0L
    }

    "track the number of responses with status code 4xx" in {
      for (_ <- 1 to 100) yield get("/async/tracing/not-found")
      serverInstruments().requestsClientError.value(resetState = false) should be > 0L
    }

    "track the number of responses with status code 5xx" in {
      for (_ <- 1 to 100) yield get("/async/tracing/error")
      serverInstruments().requestsServerError.value(resetState = false) should be > 0L
    }
  }
}
