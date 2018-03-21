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

package kamon.servlet.v25.server

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

import javax.servlet.ServletResponse
import javax.servlet.http.{HttpServletResponse, HttpServletResponseWrapper}
import kamon.servlet.server.ResponseServlet

case class ResponseServletV25(underlineResponse: HttpServletResponse) extends ResponseServlet {
  override def status: Int = {
    StatusResponseExtractor.status(underlineResponse).getOrElse(ResponseServletV25.defaultStatus)
  }
}

object ResponseServletV25 {

  // The Servlet spec says: calling setStatus is optional, if no status is set, the default is OK.
  @inline def defaultStatus: Int = HttpServletResponse.SC_OK

  def apply(response: ServletResponse): ResponseServletV25 = {
    new ResponseServletV25(ResponseWithStatusV25(response))
  }

  def apply(response: HttpServletResponse): ResponseServletV25 = {
    new ResponseServletV25(ResponseWithStatusV25(response))
  }



}

/**
  * Credits to: https://github.com/openzipkin/brave
  * This class is taken from: brave.servlet.ServletRuntime.Servlet25
  */
object StatusResponseExtractor {

  private val MaxSizeCache = 10
  private val classesToCheck = new ConcurrentHashMap[Class[_], Either[Unit, Method]]()

  def status(response: HttpServletResponse): Option[Int] = {
    response match {
      case adapter: ResponseWithStatusV25 => Option(adapter.getStatus)
      case _                              => tryToUseGetStatus(response)
    }
  }

  @inline private def tryToUseGetStatus(response: HttpServletResponse): Option[Int] = {
    val clazz = response.getClass
    Option(classesToCheck.get(clazz)) match {
      case Some(Left(_)) => None
      case Some(Right(method)) =>
        try {
          Some(method.invoke(response).asInstanceOf[Int])
        } catch {
          case _: Throwable =>
            classesToCheck.put(clazz, Left(()))
            None
        }
      case None if classesToCheck.size >= MaxSizeCache => None
      case _ =>
        if (clazz.isLocalClass || clazz.isAnonymousClass) None
        else {
          try {
            // we don't check for accessibility as isAccessible is deprecated: just fail later
            val statusMethod: Method = clazz.getMethod("getStatus")
            val code = statusMethod.invoke(response).asInstanceOf[Int]
            classesToCheck.put(clazz, Right(statusMethod))
            Some(code)
          } catch {
            case _: Throwable =>
              classesToCheck.put(clazz, Left(()))
              None
          }
        }
    }
  }
}

final class ResponseWithStatusV25(response: HttpServletResponse) extends HttpServletResponseWrapper(response) {

  // The Servlet spec says: calling setStatus is optional, if no status is set, the default is OK.
  @volatile private var httpStatus = ResponseServletV25.defaultStatus

  override def setStatus(sc: Int, sm: String): Unit = {
    httpStatus = sc
    super.setStatus(sc, sm)
  }

  override def sendError(sc: Int): Unit = {
    httpStatus = sc
    super.sendError(sc)
  }

  override def sendError(sc: Int, msg: String): Unit = {
    httpStatus = sc
    super.sendError(sc, msg)
  }

  override def setStatus(sc: Int): Unit = {
    httpStatus = sc
    super.setStatus(sc)
  }

  def getStatus: Int = httpStatus
}

object ResponseWithStatusV25 {
  def apply(response: HttpServletResponse): ResponseWithStatusV25 = new ResponseWithStatusV25(response)
  def apply(response: ServletResponse): ResponseWithStatusV25 = new ResponseWithStatusV25(response.asInstanceOf[HttpServletResponse])
}
