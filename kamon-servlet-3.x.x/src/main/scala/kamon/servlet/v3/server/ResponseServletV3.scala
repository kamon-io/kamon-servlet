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

package kamon.servlet.v3.server

import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletResponse
import kamon.servlet.server.ResponseServlet

class ResponseServletV3(val underlineResponse: HttpServletResponse) extends ResponseServlet {

  override def statusCode: Int = underlineResponse.getStatus

  override def write(header: String, value: String): Unit = underlineResponse.addHeader(header, value)
}

object ResponseServletV3 {

  def apply(request: ServletResponse): ResponseServletV3 = {
    new ResponseServletV3(request.asInstanceOf[HttpServletResponse])
  }
}
