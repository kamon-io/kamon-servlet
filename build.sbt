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

val kamonCore         = "io.kamon"                  %% "kamon-core"             % kamonVersion
val kamonTestkit      = "io.kamon"                  %% "kamon-testkit"          % kamonVersion

val servletApiV25     = "javax.servlet"             % "servlet-api"             % "2.5"
val servletApiV3      = "javax.servlet"             %  "javax.servlet-api"      % "3.0.1"
val jettyServerV9     = "org.eclipse.jetty"         %  "jetty-server"           % jettyV9Version
val jettyServletV9    = "org.eclipse.jetty"         %  "jetty-servlet"          % jettyV9Version
val jettyServletsV9   = "org.eclipse.jetty"         %  "jetty-servlets"         % jettyV9Version

val jettyServerV7     = "org.eclipse.jetty"         %  "jetty-server"           % jettyV7Version
val jettyServletV7    = "org.eclipse.jetty"         %  "jetty-servlet"          % jettyV7Version
val jettyServletsV7   = "org.eclipse.jetty"         %  "jetty-servlets"         % jettyV7Version
val httpClient        = "org.apache.httpcomponents" %  "httpclient"             % "4.5.5"
val logbackClassic    = "ch.qos.logback"            %  "logback-classic"        % "1.0.13"
val scalatest         = "org.scalatest"             %% "scalatest"              % "3.0.1"


lazy val root = (project in file("."))
  .settings(noPublishing: _*)
  .aggregate(kamonServlet, kamonServlet25, kamonServlet3, kamonServletBench25, kamonServletBench3)

val commonSettings = Seq(
  scalaVersion := "2.12.5",
  resolvers += Resolver.mavenLocal,
  crossScalaVersions := Seq("2.12.5", "2.11.12", "2.10.7"),
  scalacOptions ++= Seq(
//    "-Ypartial-unification",
    "-language:higherKinds",
    "-language:postfixOps") ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2,10)) => Seq("-Yno-generic-signatures", "-target:jvm-1.7")
    case Some((2,11)) => Seq("-Ybackend:GenBCode","-Ydelambdafy:method","-target:jvm-1.8")
    case Some((2,12)) => Seq("-opt:l:method")
    case _ => Seq.empty
  })
)

lazy val kamonServlet = Project("kamon-servlet", file("kamon-servlet"))
  .settings(moduleName := "kamon-servlet")
  .settings(parallelExecution in Test := false)
  .settings(commonSettings: _*)
  .settings(noPublishing: _*)
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
      testScope(scalatest, kamonTestkit, logbackClassic, jettyServletsV7, jettyServerV7, jettyServletV7, httpClient))
  .dependsOn(kamonServlet)

lazy val kamonServlet3 = Project("kamon-servlet-3", file("kamon-servlet-3.x.x"))
  .settings(moduleName := "kamon-servlet-3.x.x")
  .settings(parallelExecution in Test := false)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++=
      compileScope(kamonCore) ++
      providedScope(servletApiV3) ++
      testScope(scalatest, kamonTestkit, logbackClassic, jettyServletsV9, jettyServerV9, jettyServletV9, httpClient))
  .dependsOn(kamonServlet)

lazy val kamonServletBench25 = Project("benchmarks-25", file("kamon-servlet-bench-2.5"))
  .enablePlugins(JmhPlugin)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-servlet-bench-2.5",
    fork in Test := true)
  .settings(noPublishing: _*)
  .settings(
    libraryDependencies ++=
      compileScope(jettyServletsV7, jettyServerV7, jettyServletV7, httpClient) ++
        providedScope(servletApiV25))
  .dependsOn(kamonServlet25)

lazy val kamonServletBench3 = Project("benchmarks-3", file("kamon-servlet-bench-3.x.x"))
  .enablePlugins(JmhPlugin)
  .settings(commonSettings: _*)
  .settings(
    moduleName := "kamon-servlet-bench-3",
    fork in Test := true)
  .settings(noPublishing: _*)
  .settings(
    libraryDependencies ++=
      compileScope(jettyServletsV9, jettyServerV9, jettyServletV9, httpClient) ++
        providedScope(servletApiV3))
  .dependsOn(kamonServlet3)
