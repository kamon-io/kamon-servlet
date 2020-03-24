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

import javax.servlet.ServletRequest
import javax.servlet.http.HttpServletRequest
import kamon.servlet.server.RequestServlet
import kamon.servlet.utils.MapUtils
import org.slf4j.LoggerFactory

case class RequestServletV25(underlineRequest: HttpServletRequest,
                             override val host: String,
                             override val port: Int) extends RequestServlet {

  import RequestServletV25._

  lazy val headers: Map[String, String] = {
    MapUtils.caseInsensitiveMap(decodeHeaders(underlineRequest))
  }

  override def url: String = underlineRequest.getRequestURL.toString

  override def path: String = underlineRequest.getRequestURI

  override def method: String = underlineRequest.getMethod

  override def read(header: String): Option[String] = headers.get(header)

  override def readAll(): Map[String, String] = headers
}

object RequestServletV25 {

  import kamon.trace.SpanPropagation.B3.{Headers => B3Headers}

  private val log = LoggerFactory.getLogger(classOf[RequestServletV25])

  def apply(request: ServletRequest, interface: String, port: Int): RequestServletV25 = {
    new RequestServletV25(request.asInstanceOf[HttpServletRequest], interface, port)
  }

  def decodeHeaders(request: HttpServletRequest): Map[String, String] = {
    val headersIterator = request.getHeaderNames
    try {
      if (headersIterator == null) {
        log.warn("HttpServletRequest.getHeaderNames returns null")
        RequestServletV25.tracingHeaders
          .flatMap(headerName => {
            val headerValue = request.getHeader(headerName)
            if (headerValue == null) List()
            else List(headerName -> headerValue)
          }).toMap
      } else {
        val headers = Map.newBuilder[String, String]
        while (headersIterator.hasMoreElements) {
          val name = headersIterator.nextElement().asInstanceOf[String]
          val value = request.getHeader(name)
          if (value != null) {
            headers += (name -> request.getHeader(name))
          }
        }
        headers.result()
      }
    } catch {
      case e: Throwable =>
        log.error(s"Occur an error trying to decode headers of the request", e)
        Map()
    }
  }

  // FIXME: include more headers
  val tracingHeaders = List(
    B3Headers.TraceIdentifier,
    B3Headers.ParentSpanIdentifier,
    B3Headers.SpanIdentifier,
    B3Headers.Sampled,
    B3Headers.Flags
  )
}
