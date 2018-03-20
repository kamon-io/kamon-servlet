# Kamon-Servlet <img align="right" src="https://rawgit.com/kamon-io/Kamon/master/kamon-logo.svg" height="150px" style="padding-left: 20px"/>
[![Build Status](https://travis-ci.org/kamon-io/kamon-servlet.svg?branch=master)](https://travis-ci.org/kamon-io/kamon-servlet)


### Benchmarks

Execute from your terminal:

```bash
sbt
project kamonServletBench
jmh:run -i 50 -wi 20 -f1 -t1 .*Benchmark.*
```
