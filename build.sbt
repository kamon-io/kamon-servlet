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
val jettyV9Version = "9.4.8.v20171121"
val jettyV7Version = "7.6.21.v20160908"

val kamonCore         = "io.kamon"              %% "kamon-core"             % kamonVersion
val kamonTestkit      = "io.kamon"              %% "kamon-testkit"          % kamonVersion

val servletApiV25     = "javax.servlet"         % "servlet-api"             % "2.5"
val servletApiV3      = "javax.servlet"         %  "javax.servlet-api"      % "3.0.1"
val jettyServerV9     = "org.eclipse.jetty"     %  "jetty-server"           % jettyV9Version
val jettyServletV9    = "org.eclipse.jetty"     %  "jetty-servlet"          % jettyV9Version
val jettyServletsV9   = "org.eclipse.jetty"     %  "jetty-servlets"         % jettyV9Version

val jettyServerV7     = "org.eclipse.jetty"     %  "jetty-server"           % jettyV7Version
val jettyServletV7    = "org.eclipse.jetty"     %  "jetty-servlet"          % jettyV7Version
val jettyServletsV7   = "org.eclipse.jetty"     %  "jetty-servlets"         % jettyV7Version
val sttp              = "com.softwaremill.sttp" %% "core"                   % "1.1.10"
val logbackClassic    = "ch.qos.logback"        %  "logback-classic"        % "1.0.13"
val scalatest         = "org.scalatest"         %% "scalatest"              % "3.0.1"


lazy val root = (project in file("."))
    .aggregate(kamonServlet, kamonServlet25, kamonServlet3, kamonServletBench25, kamonServletBench3)

val commonSettings = Seq(
  scalaVersion := "2.12.4",
  resolvers += Resolver.mavenLocal,
  resolvers += Resolver.bintrayRepo("kamon-io", "snapshots"),
  crossScalaVersions := Seq("2.12.4", "2.11.12", "2.10.6"),
  scalacOptions ++= Seq("-Ypartial-unification", "-language:higherKinds", "-language:postfixOps"),
  scalacOptions ++= Seq("l:method", "l:classpath", "l:project")
)

lazy val kamonServlet = (project in file("kamon-servlet"))
  .settings(name := "kamon-servlet")
  .settings(parallelExecution in Test := false)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore) ++
      testScope(scalatest, kamonTestkit, logbackClassic))

lazy val kamonServlet25 = Project("kamon-servlet-25", file("kamon-servlet-2.5"))
  .settings(moduleName := "kamon-servlet-2.5")
  .settings(parallelExecution in Test := false)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore) ++
      providedScope(servletApiV25) ++
      testScope(scalatest, kamonTestkit, logbackClassic, jettyServletsV7, jettyServerV7, jettyServletV7, sttp))
  .dependsOn(kamonServlet)

lazy val kamonServlet3 = Project("kamon-servlet-3", file("kamon-servlet-3.x.x"))
  .settings(moduleName := "kamon-servlet-3.x.x")
  .settings(parallelExecution in Test := false)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore) ++
      providedScope(servletApiV3) ++
      testScope(scalatest, kamonTestkit, logbackClassic, jettyServletsV9, jettyServerV9, jettyServletV9, sttp))
  .dependsOn(kamonServlet)

lazy val kamonServletBench25 = Project("benchmarks-25", file("kamon-servlet-bench-2.5"))
  .enablePlugins(JmhPlugin)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-servlet-bench-2.5",
    fork in Test := true)
  .settings(
    libraryDependencies ++=
      compileScope(jettyServletsV7, jettyServerV7, jettyServletV7, sttp) ++
        providedScope(servletApiV25))
  .dependsOn(kamonServlet25)

lazy val kamonServletBench3 = Project("benchmarks-3", file("kamon-servlet-bench-3.x.x"))
  .enablePlugins(JmhPlugin)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-servlet-bench-3",
    fork in Test := true)
  .settings(
    libraryDependencies ++=
      compileScope(jettyServletsV9, jettyServerV9, jettyServletV9, sttp) ++
        providedScope(servletApiV3))
  .dependsOn(kamonServlet3)

def compileScope(deps: ModuleID*): Seq[ModuleID]  = deps map (_ % "compile")
def testScope(deps: ModuleID*): Seq[ModuleID]     = deps map (_ % "test")
def providedScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
def optionalScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")
