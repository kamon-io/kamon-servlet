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
import kamon.Configuration.OnReconfigureHook
import kamon.Kamon
import kamon.servlet.server.RequestServlet
import kamon.util.DynamicAccess

object Servlet {
  @volatile private var nameGenerator: NameGenerator = nameGeneratorFromConfig(Kamon.config())
  @volatile private var _server: Server = Server(Kamon.config())
  @volatile private var _tags: Tags = Tags(Kamon.config())

  def generateOperationName(request: RequestServlet): String = nameGenerator.generateOperationName(request)
  def server: Server = _server
  def tags: Tags = _tags

  private def nameGeneratorFromConfig(config: Config): NameGenerator = {
    val dynamic = new DynamicAccess(getClass.getClassLoader)
    val nameGeneratorFQCN = config.getString("kamon.servlet.name-generator")
    dynamic.createInstanceFor[NameGenerator](nameGeneratorFQCN, Nil)
  }

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit = {
      nameGenerator = nameGeneratorFromConfig(newConfig)
      _server = Server(newConfig)
      _tags = Tags(newConfig)
    }
  })

  case class Server(config: Config) {
    val interface: String = config.getString("kamon.servlet.server.interface")
    val port: Int = config.getInt("kamon.servlet.server.port")
  }

  case class Tags(config: Config) {
    val serverComponent: String = config.getString("kamon.servlet.tags.server-component")
  }
}


trait NameGenerator {
  def generateOperationName(request: RequestServlet): String
}

class DefaultNameGenerator extends NameGenerator {

  import java.util.Locale

  import scala.collection.concurrent.TrieMap

  private val localCache = TrieMap.empty[String, String]
  private val normalizePattern = """\/(\d+)""".r

  override def generateOperationName(request: RequestServlet): String = {

    localCache.getOrElseUpdate(s"${request.method}${request.url}", {
      // Convert paths of form GET /foo/bar/$paramname<regexp>/blah to foo.bar.paramname.blah.get
      val uri = request.url
      val p = normalizePattern.replaceAllIn(uri, "/#").replace('/', '.').dropWhile(_ == '.')
      val normalisedPath = {
        if (p.lastOption.exists(_ != '.')) s"$p."
        else p
      }
      s"$normalisedPath${request.method.toLowerCase(Locale.ENGLISH)}"
    })
  }
}
