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

package kamon.servlet.v3

import java.time.temporal.ChronoUnit

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.servlet.v3.client.HttpClientSupport
import kamon.servlet.v3.server.{AsyncTestServlet, JettySupport}
import kamon.servlet.{Servlet => KServlet}
import kamon.tag.Lookups.{plain, plainLong}
import kamon.testkit.TestSpanReporter
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.concurrent.duration._

class AsyncServerInstrumentationSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll
  with Eventually
  with OptionValues
  with TestSpanReporter
  with JettySupport
  with HttpClientSupport {

  override val servlet: AsyncTestServlet = AsyncTestServlet()()

  override protected def beforeAll(): Unit = {
    startServer()
    applyConfig(
      s"""
         |kamon {
         |  metric.tick-interval = 10 millis
         |  trace.tick-interval = 10 millis
         |  trace.sampler = "always"
         |  servlet.server.interface = "0.0.0.0"
         |  servlet.server.port = $port
         |}
         |
    """.stripMargin
    )
  }

  override protected def afterAll(): Unit = {
    stopServer()
  }

  "The Server instrumentation on Async Servlet 3.x.x" should {
    "propagate the current context and respond to the ok action" in {

      get("/async/tracing/ok").getStatusLine.getStatusCode shouldBe 200

      eventually(timeout(3 seconds)) {

        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "async.tracing.ok.get"
        span.kind shouldBe "server"
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith ("/async/tracing/ok")
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200

        span.from.until(span.to, ChronoUnit.MILLIS) shouldBe >=(servlet.durationOk.toLong)

      }
    }

    "propagate the current context and respond to the not-found action" in {

      get("/async/tracing/not-found").getStatusLine.getStatusCode shouldBe 404

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "not-found"
        span.kind shouldBe "server"
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith ("/async/tracing/not-found")
        span.metricTags.get(plainLong("http.status_code")) shouldBe 404

        span.from.until(span.to, ChronoUnit.MILLIS) shouldBe >=(servlet.durationNotFound.toLong)
      }
    }

    "propagate the current context and respond to the error action" in {
      get("/async/tracing/error").getStatusLine.getStatusCode shouldBe 500

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "async.tracing.error.get"
        span.kind shouldBe "server"
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith ("/async/tracing/error")
        span.hasError shouldBe true
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500

        span.from.until(span.to, ChronoUnit.MILLIS) shouldBe >=(servlet.durationError.toLong)
      }
    }

    "propagate the current context and respond to a servlet with abnormal termination" in {
      get("/async/tracing/exception").getStatusLine.getStatusCode shouldBe 500

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "async.tracing.exception.get"
        span.kind shouldBe "server"
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith ("/async/tracing/exception")
        span.hasError shouldBe true
        // FIXME
        //        spanTags("error.object") shouldBe TracingContinuation.errorMessage
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500
      }
    }
  }
}
