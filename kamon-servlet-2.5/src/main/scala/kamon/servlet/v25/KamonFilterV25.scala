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

import javax.servlet._
import kamon.servlet.KamonFilter
import kamon.servlet.v25.server.{FilterDelegationV25, RequestServletV25, ResponseProcessingContinuation, ResponseServletV25}

/**
  * Kamon Filter to tracing propagation and metrics gathering on a Servlet-Based Web App
  *
  * Concrete filter implementation for Servlet v2.5
  */
class KamonFilterV25 extends Filter with KamonFilter {

  override type Request           = RequestServletV25
  override type Response          = ResponseServletV25
  override type ChainContinuation = ResponseProcessingContinuation
  override type Chain             = FilterDelegationV25

  override def init(filterConfig: FilterConfig): Unit = ()

  override def destroy(): Unit = ()

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    executeAround(RequestServletV25(request), ResponseServletV25(response), FilterDelegationV25(chain))
  }
}
