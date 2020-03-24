
lazy val root = project in file(".") dependsOn RootProject(uri("git://github.com/kamon-io/kamon-sbt-umbrella.git#kamon-2.x"))
lazy val latestSbtUmbrella = uri("git://github.com/kamon-io/kamon-sbt-umbrella.git")

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
