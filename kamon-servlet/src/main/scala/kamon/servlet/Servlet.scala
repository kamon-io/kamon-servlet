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

import com.typesafe.config.Config
import kamon.{Kamon, OnReconfigureHook}
import kamon.servlet.server.RequestServlet
import kamon.util.DynamicAccess

object Servlet {
  @volatile var nameGenerator: NameGenerator = nameGeneratorFromConfig(Kamon.config())

  def generateOperationName(request: RequestServlet): String = nameGenerator.generateOperationName(request)

  private def nameGeneratorFromConfig(config: Config): NameGenerator = {
    val dynamic = new DynamicAccess(getClass.getClassLoader)
    val nameGeneratorFQCN = config.getString("kamon.servlet.name-generator")
    dynamic.createInstanceFor[NameGenerator](nameGeneratorFQCN, Nil).get
  }

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit = {
      nameGenerator = nameGeneratorFromConfig(newConfig)
    }
  })
}

trait NameGenerator {
  def generateOperationName(request: RequestServlet): String
}

class DefaultNameGenerator extends NameGenerator {

  import java.util.Locale

  import scala.collection.concurrent.TrieMap

  private val localCache = TrieMap.empty[String, String]
  private val normalizePattern = """\$([^<]+)<[^>]+>""".r

  override def generateOperationName(request: RequestServlet): String = {

    localCache.getOrElseUpdate(s"${request.getMethod}${request.uri}", {
      // Convert paths of form GET /foo/bar/$paramname<regexp>/blah to foo.bar.paramname.blah.get
      val uri = request.uri
      val p = normalizePattern.replaceAllIn(uri, "$1").replace('/', '.').dropWhile(_ == '.')
      val normalisedPath = {
        if (p.lastOption.exists(_ != '.')) s"$p."
        else p
      }
      s"$normalisedPath${request.getMethod.toLowerCase(Locale.ENGLISH)}"
    })
  }
}
