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

package kamon.servlet.v3.server

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

object Servlets {

  val defaultDuration: Long = 100 // millis
  val hardcodedId = 10

  def withDelay[A](timeInMillis: Long)(thunk: => A): A = {
    if (timeInMillis > 0) Thread.sleep(timeInMillis)
    thunk
  }
}

case class AsyncTestServlet(defaultDuration: Long = Servlets.defaultDuration)
                           (val durationOk: Long = defaultDuration,
                            val durationNotFound: Long = defaultDuration,
                            val durationError: Long = defaultDuration,
                            val durationSlowly: Long = defaultDuration) extends HttpServlet {
  import Servlets._


  override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
    val asyncContext = req.startAsync(req, resp)

    asyncContext.start(() => {
      asyncContext.getRequest.asInstanceOf[HttpServletRequest].getRequestURI match {
        case "/async/tracing/not-found" ⇒ withDelay(durationNotFound) { resp.setStatus(404) }
        case "/async/tracing/error"     ⇒ withDelay(durationError) { resp.setStatus(500) }
        case "/async/tracing/exception" ⇒ throw new RuntimeException("Blowing up from internal servlet")
        case "/async/tracing/ok"        ⇒ withDelay(durationOk) { resp.setStatus(200) }
        case path if path == s"/async/tracing/ok/$hardcodedId" ⇒ withDelay(durationOk) { resp.setStatus(200) }
        case "/async/tracing/slowly"    ⇒ withDelay(durationSlowly) { resp.setStatus(200) }
        case other                      ⇒
          resp.getOutputStream.println(s"Something wrong on the test. Endpoint unmapped: $other")
          resp.setStatus(404)
      }
      asyncContext.complete()
    })
  }
}

case class SyncTestServlet(defaultDelay: Long = 1000) extends HttpServlet {
  import Servlets._

  override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = req.getRequestURI match {
    case "/sync/tracing/not-found" ⇒ resp.setStatus(404)
    case "/sync/tracing/error"     ⇒ resp.setStatus(500)
    case "/sync/tracing/exception" ⇒ throw new RuntimeException("Blowing up from internal servlet")
    case "/sync/tracing/ok"        ⇒ resp.setStatus(200)
    case "/sync/tracing/ok/10"        ⇒ resp.setStatus(200)
    case path if path == s"/sync/tracing/ok/$hardcodedId" ⇒ resp.setStatus(200)
    case "/sync/tracing/slowly"    ⇒ withDelay(defaultDelay) { resp.setStatus(200) }
    case other                     ⇒
      resp.getOutputStream.println(s"Something wrong on the test. Endpoint unmapped: $other")
      resp.setStatus(404)
  }
}
