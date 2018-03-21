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

package kamon.servlet.v25

import java.util.concurrent.Executors

import kamon.Kamon
import kamon.servlet.Metrics.{GeneralMetrics, ResponseTimeMetrics}
import kamon.servlet.v25.server.{JettySupport, SyncTestServlet}
import kamon.testkit.MetricInspection
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.concurrent.{Await, ExecutionContext, Future}


class HttpMetricsSpec extends WordSpec
  with Matchers
  with Eventually
  with SpanSugar
  with MetricInspection
  with OptionValues
  with SpanReporter
  with BeforeAndAfterAll
  with JettySupport {

  import com.softwaremill.sttp._
  implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  override val servlet = SyncTestServlet()

  override protected def beforeAll(): Unit = {
    startServer()
    startRegistration()
    Kamon.config()
  }

  override protected def afterAll(): Unit = {
    stopRegistration()
    stopServer()
    Await.result(Kamon.stopAllReporters(), 2 seconds)
  }

  private def get(path: String): Id[Response[String]] = {
    sttp.get(Uri("localhost", port).path(path)).send()
  }

  private val parallelRequestExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(15))

  "The Http Metrics generation" should {
    "track the total of active requests" in {
      for(_ <- 1 to 10) yield  {
        Future { get("/sync/tracing/slowly") }(parallelRequestExecutor)
      }

      eventually(timeout(3 seconds)) {
        GeneralMetrics().activeRequests.distribution().max shouldBe 10L
      }

      eventually(timeout(3 seconds)) {
        GeneralMetrics().activeRequests.distribution().min shouldBe 0L
      }
      reporter.clear()
    }

    "track the response time with status code 2xx" in {
      for(_ <- 1 to 100) yield get("/sync/tracing/ok")
      ResponseTimeMetrics().forStatusCode("2xx").distribution().max should be > 0L
    }

    "track the response time with status code 4xx" in {
      for(_ <- 1 to 100) yield get("/sync/tracing/not-found")
      ResponseTimeMetrics().forStatusCode("4xx").distribution().max should be > 0L
    }

    "track the response time with status code 5xx" in {
      for(_ <- 1 to 100) yield get("/sync/tracing/error")
      ResponseTimeMetrics().forStatusCode("5xx").distribution().max should be > 0L
    }
  }
}
