package kamon.servlet.v3

import com.typesafe.config.Config
import javax.servlet.http.HttpServletResponse
import kamon.Configuration.OnReconfigureHook
import kamon.Kamon
import kamon.servlet.v3.server.ResponseServletV3

object KamonFilterV3Config {
  @volatile var errorResponseHandler: ErrorResponseHandler = ErrorResponseHandler(Kamon.config)

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit = {
      errorResponseHandler = ErrorResponseHandler(newConfig)
    }
  })
}

case class ErrorResponseHandler(config: Config) {

  def withRightStatus(response: ResponseServletV3): ResponseServletV3 = {
    if (config.getBoolean("kamon.servlet.error-status-correction"))
      new WithStatusCorrection(response.underlineResponse)
    else
      response
  }

  private final class WithStatusCorrection(override val underlineResponse: HttpServletResponse)
    extends ResponseServletV3(underlineResponse) {

    override def statusCode: Int = {
      val s = super.statusCode
      if (s == 200) 500
      else s
    }
  }

}
