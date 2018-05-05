package kamon.servlet.v25

import com.typesafe.config.Config
import javax.servlet.http.HttpServletResponse
import kamon.servlet.v25.server.ResponseServletV25
import kamon.{Kamon, OnReconfigureHook}

object KamonFilterV25Config {
  @volatile var errorResponseHandler: ErrorResponseHandler = ErrorResponseHandler(Kamon.config)

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit = {
      errorResponseHandler = ErrorResponseHandler(newConfig)
    }
  })
}

case class ErrorResponseHandler(config: Config) {

  def withRightStatus(response: ResponseServletV25): ResponseServletV25 = {
    if (config.getBoolean("kamon.servlet.error-status-correction"))
      new WithStatusCorrection(response.underlineResponse)
    else
      response
  }

  private final class WithStatusCorrection(override val underlineResponse: HttpServletResponse)
    extends ResponseServletV25(underlineResponse) {

    override def status: Int = {
      val s = super.status
      if (s == 200) 500
      else s
    }
  }
}
