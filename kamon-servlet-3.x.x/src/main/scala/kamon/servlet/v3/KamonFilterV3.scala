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

import javax.servlet._
import kamon.servlet.KamonFilter
import kamon.servlet.v3.server.{FilterDelegationV3, RequestServletV3, ResponseProcessingContinuation, ResponseServletV3}

/**
  * Kamon Filter to tracing propagation and metrics gathering on a Servlet-Based Web App
  *
  * Concrete filter implementation for Servlet v3.x.x
  */
class KamonFilterV3 extends Filter with KamonFilter {

  override type Request           = RequestServletV3
  override type Response          = ResponseServletV3
  override type ChainContinuation = ResponseProcessingContinuation
  override type Chain             = FilterDelegationV3

  override def init(filterConfig: FilterConfig): Unit = ()

  override def destroy(): Unit = ()

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    executeAround(RequestServletV3(request), ResponseServletV3(response), FilterDelegationV3(chain))
  }
}
