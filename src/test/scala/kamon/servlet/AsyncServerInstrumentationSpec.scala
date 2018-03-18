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

package kamon.servlet

import java.time.temporal.ChronoUnit
import javax.servlet.Servlet

import kamon.Kamon
import kamon.servlet.server.{AsyncTestServlet, JettySupport, Servlets}
import kamon.trace.Span
import kamon.trace.Span.TagValue
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, OptionValues, WordSpec}

import scala.concurrent.duration._
import scala.language.postfixOps

class AsyncServerInstrumentationSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll
  with Eventually
  with OptionValues
  with SpanReporter
  with JettySupport {

  import com.softwaremill.sttp._
  implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  override val servlet = AsyncTestServlet()()

  override protected def beforeAll(): Unit = {
    startServer()
    startRegistration()
    Kamon.config()
  }

  override protected def afterAll(): Unit = {
    stopRegistration()
    stopServer()
  }

  private def get(path: String): Id[Response[String]] = {
    sttp.get(Uri("localhost", port).path(path)).send()
  }

  "The Async Server instrumentation" should {
    "propagate the current context and respond to the ok action" in {

      get("/async/tracing/ok").code shouldBe 200

      eventually(timeout(3 seconds)) {

        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "async.tracing.ok.get"
        spanTags("span.kind") shouldBe "server"
        spanTags("component") shouldBe "servlet.server"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe "/async/tracing/ok"
        span.tags("http.status_code") shouldBe TagValue.Number(200)

        span.from.until(span.to, ChronoUnit.MILLIS) shouldBe >= (servlet.durationOk.toLong)

      }
    }

    "propagate the current context and respond to the not-found action" in {

      get("/async/tracing/not-found").code shouldBe 404

      eventually(timeout(3 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "not-found"
        spanTags("span.kind") shouldBe "server"
        spanTags("component") shouldBe "servlet.server"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe "/async/tracing/not-found"
        span.tags("http.status_code") shouldBe TagValue.Number(404)

        span.from.until(span.to, ChronoUnit.MILLIS) shouldBe >= (servlet.durationNotFound.toLong)
      }
    }

    "propagate the current context and respond to the error action" in {
      get("/async/tracing/error").code shouldBe 500

      eventually(timeout(3 seconds)) {
        val span = reporter.nextSpan().value
        val spanTags = stringTag(span) _

        span.operationName shouldBe "async.tracing.error.get"
        spanTags("span.kind") shouldBe "server"
        spanTags("component") shouldBe "servlet.server"
        spanTags("http.method") shouldBe "GET"
        spanTags("http.url") shouldBe "/async/tracing/error"
        span.tags("error") shouldBe TagValue.True
        span.tags("http.status_code") shouldBe TagValue.Number(500)

        span.from.until(span.to, ChronoUnit.MILLIS) shouldBe >= (servlet.durationError.toLong)
      }
    }
  }

  def stringTag(span: Span.FinishedSpan)(tag: String): String = {
    span.tags(tag).asInstanceOf[TagValue.String].string
  }
}
