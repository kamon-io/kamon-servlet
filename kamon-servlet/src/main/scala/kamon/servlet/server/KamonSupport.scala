//package kamon.servlet.server
//
//import com.typesafe.config.Config
//import kamon.Kamon
//import kamon.context.Storage
//import kamon.instrumentation.http.HttpServerInstrumentation.RequestHandler
//import kamon.instrumentation.http.{HttpMessage, HttpServerInstrumentation}
//import kamon.servlet.Servlet
//import kamon.servlet.utils.RequestContinuation
//
//import scala.util.Success
//
//case class KamonSupport[
//Request <: HttpMessage.Request,
//Response <: HttpMessage.Response,
//Continuation <: RequestContinuation[Request, Response],
//](interface: String, port: Int, chain: FilterDelegation[Request, Response, Continuation],
//  httpServerConfig: Config) {
//
//  private val instrumentation: HttpServerInstrumentation = HttpServerInstrumentation.from(httpServerConfig, "servlet.server", interface, port)
//  //}
//  //
//  //object KamonSupport {
//  //
//  //  def apply[
//  //  Request <: HttpMessage.Request,
//  //  Response <: HttpMessage.Response,
//  //  Continuation <: RequestContinuation[Request, Response]
//  //  ](interface: String, port: Int, chain: FilterDelegation2[Request, Response, Continuation]) = {
//  //    val httpServerConfig: Config = Kamon.config().getConfig("kamon.instrumentation.servlet_25")
//  //    val instrumentation = HttpServerInstrumentation.from(httpServerConfig, "servlet.server", interface, port)
//  //
//  //    kamonService(chain, instrumentation)(_)
//  //  }
//
//
//  def kamonService(chain: FilterDelegation2[HttpMessage.Request, HttpMessage.Response],
//                   instrumentation: HttpServerInstrumentation)
//                  (request: Request, response: Response) = Unit {
//    val requestHandler: RequestHandler = instrumentation.createHandler(request)
//    setOperationName(request, requestHandler)
//    chain.chain(request, response) match {
//      case Success(value) =>
//    }
//    getHandler(instrumentation)(request).use { handler =>
//      for {
//        resOrUnhandled <- service(request).value.attempt
//        respWithContext <- kamonServiceHandler(handler, resOrUnhandled, instrumentation.settings)
//      } yield respWithContext
//    }
//  }
//
//  //  def buildRequestMessage[Request  <: RequestServlet](inner: Request): HttpMessage.Request = new HttpMessage.Request {
//  //    override def url: String = inner.uri
//  //
//  //    override def path: String = inner.uri
//  //
//  //    override def method: String = inner.getMethod
//  //
//  //    override def host: String = inner.uri.authority.map(_.host.value).getOrElse("")
//  //
//  //    override def port: Int = inner.uri.authority.flatMap(_.port).getOrElse(0)
//  //
//  //    override def read(header: String): Option[String] = inner.headers.get(CaseInsensitiveString(header)).map(_.value)
//  //
//  //    override def readAll(): Map[String, String] = {
//  //      val builder = Map.newBuilder[String, String]
//  //      inner.headers.foreach(h => builder += (h.name.value -> h.value))
//  //      builder.result()
//  //    }
//  //  }
//
//  private def processRequest[F[_]](requestHandler: RequestHandler)(implicit F: Sync[F]): Resource[F, RequestHandler] =
//    Resource.make(F.delay(requestHandler.requestReceived()))(h => F.delay(h.responseSent()))
//
//  private def withContext[F[_]](requestHandler: RequestHandler)(implicit F: Sync[F]): Resource[F, Storage.Scope] =
//    Resource.make(F.delay(Kamon.storeContext(requestHandler.context)))(scope => F.delay(scope.close()))
//
//
//  //  private def getHandler(instrumentation: HttpServerInstrumentation)(request: Request): RequestHandler =
//  //    for {
//  //      handler <- instrumentation.createHandler(buildRequestMessage(request))
//  //      _ <- processRequest(handler)
//  //      _ <- withContext(handler)
//  //    } yield handler
//
//  private def kamonServiceHandler(requestHandler: RequestHandler,
//                                  e: Either[Throwable, Option[Response]],
//                                        settings: HttpServerInstrumentation.Settings): Option[Response] =
//    e match {
//      case Left(e) =>
//        F.delay {
//          requestHandler.span.fail(e.getMessage)
//          Some(requestHandler.buildResponse(errorResponseBuilder, requestHandler.context))
//        } *> F.raiseError(e)
//      case Right(None) =>
//        F.delay {
//          requestHandler.span.name(settings.unhandledOperationName)
//          val response: Response[F] = requestHandler.buildResponse[Response[F]](
//            notFoundResponseBuilder, requestHandler.context
//          )
//          Some(response)
//        }
//      case Right(Some(response)) =>
//        F.delay {
//          val a = requestHandler.buildResponse(getResponseBuilder(response), requestHandler.context)
//          Some(a)
//        }
//    }
//
//
//  private def setOperationName(request: Request, handler: RequestHandler): Unit = {
//    handler.span.name(Servlet.generateOperationName(request))
//  }
//}
