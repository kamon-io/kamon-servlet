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

package kamon.servlet.v25

import java.util.concurrent.Executors

import kamon.instrumentation.http.HttpServerMetrics
import kamon.servlet.Servlet
import kamon.servlet.v25.client.HttpClientSupport
import kamon.servlet.v25.server.{JettySupport, SyncTestServlet}
import kamon.testkit.{InstrumentInspection, TestSpanReporter}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, OptionValues}

import scala.concurrent.{ExecutionContext, Future}


class HttpMetricsSpec extends AnyWordSpec
  with Matchers
  with Eventually
  with SpanSugar
  with InstrumentInspection.Syntax
  with OptionValues
  with TestSpanReporter
  with BeforeAndAfterAll
  with JettySupport
  with HttpClientSupport {

  override val servlet = SyncTestServlet()

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

  "The Http Metrics generation on Servlet 2.5" should {
    "track the total of active requests" in {
      for (_ <- 1 to 10) yield {
        Future {
          get("/sync/tracing/slowly")
        }(parallelRequestExecutor)
      }

      eventually(timeout(5 seconds)) {
        serverInstruments().activeRequests.distribution().max should (be >= 0L and be <= 10L)
      }

      eventually(timeout(5 seconds)) {
        serverInstruments().activeRequests.distribution().min should (be >= 0L and be <= 10L)
      }
      testSpanReporter().clear()
    }

    "track the number of responses with status code 2xx" in {
      for (_ <- 1 to 100) yield get("/sync/tracing/ok")
      serverInstruments().requestsSuccessful.value(resetState = false) should be >= 0L
    }

    "track the number of responses with status code 4xx" in {
      for (_ <- 1 to 100) yield get("/sync/tracing/not-found")
      serverInstruments().requestsClientError.value(resetState = false) should be >= 0L
    }

    "track the number of responses with status code 5xx" in {
      for (_ <- 1 to 100) yield get("/sync/tracing/error")
      serverInstruments().requestsServerError.value(resetState = false) should be >= 0L
    }
  }
}
