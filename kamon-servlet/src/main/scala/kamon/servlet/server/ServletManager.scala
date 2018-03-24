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

package kamon.servlet.server

import kamon.servlet.utils.RequestContinuation

import scala.util.Try


trait RequestServlet {

  def getMethod: String
  def uri: String
  def url: String
  def headers: Map[String, String]
}

trait ResponseServlet {

  def status: Int
}

trait FilterDelegation[Request  <: RequestServlet, Response <: ResponseServlet, Continuation <: RequestContinuation] {

  def chain(request: Request, response: Response)(continuation: Continuation): Try[Unit]

  def fromUppers(continuations: RequestContinuation*): Continuation
}
