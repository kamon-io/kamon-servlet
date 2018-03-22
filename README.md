# Kamon-Servlet <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>
[![Build Status](https://travis-ci.org/kamon-io/kamon-servlet.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-servlet)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/kamon-io/Kamon?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-servlet_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.kamon/kamon-servlet_2.12)


### Getting Started

The `kamon-servlet` module brings traces and metrics to your [servlet][1] based applications.

<b>Kamon Servlet</b> is currently available for Scala 2.10, 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon-servlet-2.5  | status | jdk        | scala            
|:---------------:|:------:|:----------:|------------------
|  -          | - | 1.7+, 1.8+ | 2.10, 2.11, 2.12

| kamon-servlet-3.x.x  | status | jdk        | scala   
|:---------------:|:------:|:----------:|------------------
|  -          | - | 1.7+, 1.8+       | 2.10, 2.11, 2.12  

To get `kamon-servlet` in your project:

*TODO*


### Setting up
To enable `kamon-servlet` on your app all you need to do is to add the Kamon Filter:

* For Servlet 2.5: [`kamon.servlet.v25.KamonFilterV25`][2]
* For Servlet 3.x.x: [`kamon.servlet.v3.KamonFilterV3`][3]

You should register it in the specific way your framework required. Otherwise,
you need to configure it manually. Below there are some example.

#### Servlet v3+

You could programmatically register it using a `ServletContextListener`:

```java
package kamon.servlet.v3.example;

import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import kamon.Kamon;
import kamon.servlet.v3.KamonFilterV3;

public class KamonContextListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    // here you might subscribe all reporters you want. e.g. `Kamon.addReporter(new PrometheusReporter())`
    servletContextEvent
        .getServletContext()
        .addFilter("KamonFilter", new KamonFilterV3())
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }

  @Override
  public void contextDestroyed(ServletContextEvent arg0) {
    // in case you have subscribed some reporters: `Kamon.stopAllReporters();`
    System.out.println("KamonContextListener destroyed");
  }
}
```

#### Servlet v2.5

For Servlet 2.5 there isn't a programmatic way to achieve it, so you have to define it in `web.xml`:

```xml
<filter>
   <filter-name>kamonFilter</filter-name>
   <filter-class>kamon.servlet.v25.KamonFilterV25</filter-class>
 </filter>
 <filter-mapping>
   <filter-name>kamonFilter</filter-name>
   <url-pattern>/*</url-pattern>
 </filter-mapping>
```

### Micro Benchmarks

Execute from your terminal:

```bash
sbt
project benchmarks-3 # or benchmarks-25
jmh:run -i 50 -wi 20 -f1 -t1 .*Benchmark.*
```


[1]: http://www.oracle.com/technetwork/java/index-jsp-135475.html
[2]: kamon-servlet-3.x.x/src/test/java/kamon/servlet/v3/example/KamonContextListener.java
[3]: kamon-servlet-2.5/src/test/java/kamon/servlet/v25/example/KamonFilterWiring.java
