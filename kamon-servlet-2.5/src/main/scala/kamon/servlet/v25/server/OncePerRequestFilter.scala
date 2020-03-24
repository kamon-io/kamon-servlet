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

package kamon.servlet.v25.server

import javax.servlet.{Filter, FilterChain, ServletRequest, ServletResponse}

trait OncePerRequestFilter {
  self: Filter =>

  import OncePerRequestFilter._

  override def doFilter(request: ServletRequest, response: ServletResponse, filterChain: FilterChain): Unit = {
    val hasAlreadyFilteredAttribute = request.getAttribute(attribute) != null
    if (hasAlreadyFilteredAttribute)
      filterChain.doFilter(request, response)
    else
      filterOnlyOnce(request, response, filterChain)
  }

  def filterOnlyOnce(request: ServletRequest, response: ServletResponse, filterChain: FilterChain): Unit

  private lazy val attribute: String = s"${getClass.getName}.$OnlyOnceAttributePrefix"
}

object OncePerRequestFilter {
  val OnlyOnceAttributePrefix = "OnlyOnceExecuted"
}
