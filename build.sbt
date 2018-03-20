/* =========================================================================================
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

val kamonVersion = "1.1.0"
val jettyVersion = "9.4.8.v20171121"

val kamonCore         = "io.kamon"              %% "kamon-core"             % kamonVersion
val kamonTestkit      = "io.kamon"              %% "kamon-testkit"          % kamonVersion

val servletApi        = "javax.servlet"         %  "javax.servlet-api"      % "3.0.1"
val jetty             = "org.eclipse.jetty"     %  "jetty-servlets"         % jettyVersion
val jettyServer       = "org.eclipse.jetty"     %  "jetty-server"           % jettyVersion
val jettyServlet      = "org.eclipse.jetty"     %  "jetty-servlet"          % jettyVersion
val sttp              = "com.softwaremill.sttp" %% "core"                   % "1.1.10"
val logbackClassic    = "ch.qos.logback"        %  "logback-classic"        % "1.0.13"
val scalatest         = "org.scalatest"         %% "scalatest"              % "3.0.1"


lazy val root = (project in file("."))
    .aggregate(kamonServlet, kamonServletBench)

val commonSettings = Seq(
  scalaVersion := "2.12.4",
  resolvers += Resolver.mavenLocal,
  resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"),
  crossScalaVersions := Seq("2.12.4", "2.11.12", "2.10.6")
//  scalacOptions ++= Seq("l:method", "l:classpath", "l:project")
)

lazy val kamonServlet = (project in file("kamon-servlet"))
  .settings(name := "kamon-servlet")
  .settings(parallelExecution in Test := false)
  .settings(commonSettings: _*)
  .settings(scalacOptions ++= Seq("-Ypartial-unification", "-language:higherKinds"))
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore) ++
      providedScope(servletApi) ++
      testScope(scalatest, kamonTestkit, logbackClassic, jetty, jettyServer, jettyServlet, sttp))

lazy val kamonServletBench = (project in file("kamon-servlet-bench"))
  .enablePlugins(JmhPlugin)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-servlet-bench",
    fork in Test := true)
  .settings(
    libraryDependencies ++=
      compileScope(jetty, jettyServer, jettyServlet, sttp) ++
        providedScope(servletApi))
  .dependsOn(kamonServlet)

def compileScope(deps: ModuleID*): Seq[ModuleID]  = deps map (_ % "compile")
def testScope(deps: ModuleID*): Seq[ModuleID]     = deps map (_ % "test")
def providedScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
def optionalScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")
