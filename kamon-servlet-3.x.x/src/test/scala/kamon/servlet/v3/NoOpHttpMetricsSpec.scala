///*
// * =========================================================================================
// * Copyright © 2013-2018 the kamon project <http://kamon.io/>
// *
// * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// * except in compliance with the License. You may obtain a copy of the License at
// *
// *   http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software distributed under the
// * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// * either express or implied. See the License for the specific language governing permissions
// * and limitations under the License.
// * =========================================================================================
// */

package kamon.servlet.v3

import java.util.concurrent.Executors

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.servlet.Metrics.GeneralMetrics
import kamon.servlet.v3.client.HttpClientSupport
import kamon.servlet.v3.server.{JettySupport, SyncTestServlet}
import kamon.testkit.MetricInspection
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.concurrent.{ExecutionContext, Future}


class NoOpHttpMetricsSpec extends WordSpec
  with Matchers
  with Eventually
  with SpanSugar
  with MetricInspection
  with OptionValues
  with SpanReporter
  with BeforeAndAfterAll
  with JettySupport
  with HttpClientSupport {

  override val servlet: SyncTestServlet = SyncTestServlet()

  override protected def beforeAll(): Unit = {
    Kamon.reconfigure(ConfigFactory.parseString("kamon.servlet.metrics.enabled=false").withFallback(ConfigFactory.load()))
    startServer()
    startRegistration()
  }

  override protected def afterAll(): Unit = {
    stopRegistration()
    stopServer()
  }

  private val parallelRequestExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(15))

  "The NoOp HttpMetrics on Sync Servlet 3.x.x" should {
    "not generate metrics" in {

      for(_ <- 1 to 10) yield  {
        Future { get("/sync/tracing/slowly") }(parallelRequestExecutor)
      }

      eventually(timeout(3 seconds)) {
        GeneralMetrics().activeRequests.distribution().max shouldBe 0L
      }

      eventually(timeout(3 seconds)) {
        GeneralMetrics().activeRequests.distribution().min shouldBe 0L
      }
      reporter.clear()
    }
  }
}
