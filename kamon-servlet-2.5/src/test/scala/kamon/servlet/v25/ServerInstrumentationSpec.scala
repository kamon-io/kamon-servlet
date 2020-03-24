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

import kamon.servlet.v25.client.HttpClientSupport
import kamon.servlet.v25.server.Servlets.hardcodedId
import kamon.servlet.v25.server.{JettySupport, SyncTestServlet}
import kamon.servlet.{Servlet => KServlet}
import kamon.tag.Lookups.{plain, plainLong}
import kamon.testkit.TestSpanReporter
import kamon.trace.Span
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.concurrent.duration._

class ServerInstrumentationSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll
  with Eventually
  with OptionValues
  with TestSpanReporter
  with JettySupport
  with HttpClientSupport {

  override val servlet = SyncTestServlet()

  override protected def beforeAll(): Unit = {
    startServer()
    applyConfig(
      s"""
         |kamon {
         |  metric.tick-interval = 10 millis
         |  trace.tick-interval = 10 millis
         |  trace.sampler = "always"
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

  "The Server instrumentation on Servlet 2.5" should {
    "propagate the current context and respond to the ok action" in {

      get("/sync/tracing/ok").getStatusLine.getStatusCode shouldBe 200

      eventually(timeout(3 seconds)) {

        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "sync.tracing.ok.get"
        span.kind shouldBe Span.Kind.Server
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith("/sync/tracing/ok")
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200

        span.parentId.string shouldBe ""
      }
    }
    "propagate the current context and respond to the ok action removing variable numbers from operation name" in {

      get(s"/sync/tracing/ok/$hardcodedId").getStatusLine.getStatusCode shouldBe 200

      eventually(timeout(3 seconds)) {

        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "sync.tracing.ok.#.get"
        span.kind shouldBe Span.Kind.Server
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith(s"/sync/tracing/ok/$hardcodedId")
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200

        span.parentId.string shouldBe ""
      }
    }

    "propagate the current context and respond to the not-found action" in {

      get("/sync/tracing/not-found").getStatusLine.getStatusCode shouldBe 404

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "unhandled"
        span.kind shouldBe Span.Kind.Server
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith("/sync/tracing/not-found")
        span.metricTags.get(plainLong("http.status_code")) shouldBe 404

        span.parentId.string shouldBe ""
      }
    }

    "propagate the current context and respond to the error action" in {
      get("/sync/tracing/error").getStatusLine.getStatusCode shouldBe 500

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "sync.tracing.error.get"
        span.kind shouldBe Span.Kind.Server
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith("/sync/tracing/error")
        span.hasError shouldBe true
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500L

        span.parentId.string shouldBe ""
      }
    }

    "propagate the current context and respond to a servlet with abnormal termination" in {
      get("/sync/tracing/exception").getStatusLine.getStatusCode shouldBe 200

      eventually(timeout(3 seconds)) {
        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "sync.tracing.exception.get"
        span.kind shouldBe Span.Kind.Server
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith("/sync/tracing/exception")
        span.hasError shouldBe true
        //FIXME
        //        span.tags.get(plain("error.object")) shouldBe "Blowing up from internal servlet"
        span.metricTags.get(plainLong("http.status_code")) shouldBe 500L

        span.parentId.string shouldBe ""
      }
    }

    "resume the incoming context and respond to the ok action" in {
      get("/sync/tracing/ok", IncomingContext.headersB3).getStatusLine.getStatusCode shouldBe 200

      eventually(timeout(3 seconds)) {

        val span = testSpanReporter().nextSpan().value

        span.operationName shouldBe "sync.tracing.ok.get"
        span.kind shouldBe Span.Kind.Server
        span.metricTags.get(plain("component")) shouldBe KServlet.tags.serverComponent
        span.metricTags.get(plain("http.method")) shouldBe "GET"
        span.tags.get(plain("http.url")) should endWith("/sync/tracing/ok")
        span.metricTags.get(plainLong("http.status_code")) shouldBe 200

        span.parentId.string shouldBe IncomingContext.SpanId
        span.trace.id.string shouldBe IncomingContext.TraceId
      }
    }
  }

  object IncomingContext {

    import kamon.trace.SpanPropagation.B3.{Headers => B3Headers}

    val TraceId = "1234"
    val ParentSpanId = "2222"
    val SpanId = "4321"
    val Sampled = "1"
    val Flags = "some=baggage;more=baggage"


    val headersB3 = Seq(
      (B3Headers.TraceIdentifier, TraceId),
      (B3Headers.ParentSpanIdentifier, ParentSpanId),
      (B3Headers.SpanIdentifier, SpanId),
      (B3Headers.Sampled, Sampled),
      (B3Headers.Flags, Flags))

  }

}
