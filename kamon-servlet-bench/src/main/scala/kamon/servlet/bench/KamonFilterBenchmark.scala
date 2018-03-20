package kamon.servlet.bench

import java.util.concurrent.TimeUnit

import kamon.Kamon
import kamon.servlet.KamonFilter
import kamon.servlet.server.JettyServer
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
class KamonFilterBenchmark {

  import com.softwaremill.sttp._
  implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  val server = new JettyServer()
  var port: Int = 0

  @Setup(Level.Trial)
  def setup(): Unit = {
    Kamon.config()
    server.start()
    port = server.selectedPort
  }

  @TearDown(Level.Trial)
  def doTearDown(): Unit = {
    server.stop()
  }

  private def get(path: String, headers: Seq[(String, String)] = Seq()): Id[Response[String]] = {
    sttp.get(Uri("localhost", port).path(path)).headers(headers: _*).send()
  }

  /**
    * This benchmark attempts to measure the performance with tracing and metrics enabled.
    *
    * @param blackhole a { @link Blackhole} object supplied by JMH
    */
  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork
  def trace_new_span(blackhole: Blackhole): Unit = {
    blackhole.consume(get("/tracing/ok"))
  }

  /**
    * This benchmark attempts to measure the performance with tracing and metrics enabled.
    *
    * @param blackhole a { @link Blackhole} object supplied by JMH
    */
  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork
  def trace_resuming_span(blackhole: Blackhole, incomingContext: IncomingContext): Unit = {
    blackhole.consume(get("/tracing/ok"), incomingContext.headersB3)
  }

  /**
    * This benchmark attempts to measure the performance with NO tracing NEITHER metrics enabled.
    *
    * @param blackhole a { @link Blackhole} object supplied by JMH
    */
  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  @Fork
  def none_tracing(blackhole: Blackhole): Unit = {
    blackhole.consume(get("/ok"))
  }

}

@State(Scope.Benchmark)
class IncomingContext {
  val headersB3 = Seq(
    ("X-B3-TraceId", "1234"),
    ("X-B3-ParentSpanId", "2222"),
    ("X-B3-SpanId", "4321"),
    ("X-B3-Sampled", "1"),
    ("X-B3-Extra-Baggage", "some=baggage;more=baggage"))
}
