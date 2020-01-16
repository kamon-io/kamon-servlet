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

package kamon.servlet

import kamon.Kamon
import kamon.instrumentation.http.HttpServerInstrumentation
import kamon.servlet.server._
import kamon.servlet.utils.RequestContinuation
import kamon.servlet.{Servlet => KamonServlet}


/**
  * Kamon Filter to tracing propagation and metrics gathering on a Servlet-Based Web App
  */
trait KamonFilter {

  type Request <: RequestServlet
  type Response <: ResponseServlet
  type ChainContinuation <: RequestContinuation[Request, Response]
  type Chain <: FilterDelegation[Request, Response, ChainContinuation]

  lazy val instrumentation: HttpServerInstrumentation = {
    val httpServerConfig = Kamon.config().getConfig("kamon.instrumentation.servlet")
    HttpServerInstrumentation.from(httpServerConfig, KamonServlet.tags.serverComponent,
      KamonServlet.server.interface, KamonServlet.server.port)
  }

  lazy val servletTracing: ServletTracing = ServletTracing(instrumentation)

  def executeAround(request: Request, response: Response, next: Chain): Unit = {
    servletTracing.withTracing(request, response) { tracingContinuation: TracingContinuation =>
      next.chain(request, response)(next.joinContinuations(tracingContinuation))
    } get

  }

}
