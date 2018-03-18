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

import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import kamon.Kamon
import kamon.servlet.server.{ServletMetrics, ServletTracing}

import scala.language.postfixOps

class KamonFilter extends Filter {
  override def init(filterConfig: FilterConfig): Unit = ()

  override def destroy(): Unit = ()

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val req = request.asInstanceOf[HttpServletRequest]
    val res = response.asInstanceOf[HttpServletResponse]

    val start = Kamon.clock().instant()

    ServletMetrics.withMetrics(start, req, res) { metricsContinuation =>
      ServletTracing.withTracing(req, res, metricsContinuation) {
        chain.doFilter(request, response)
      }
    } get

  }

}
