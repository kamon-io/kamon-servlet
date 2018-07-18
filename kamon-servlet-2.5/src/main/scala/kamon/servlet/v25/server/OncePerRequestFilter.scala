package kamon.servlet.v25.server

import javax.servlet.{Filter, FilterChain, ServletRequest, ServletResponse}

trait OncePerRequestFilter { self: Filter =>
  import OncePerRequestFilter._

  override def doFilter(request: ServletRequest, response: ServletResponse, filterChain: FilterChain): Unit = {
    val hasAlreadyFilteredAttribute = request.getAttribute(attribute) != null
    if (hasAlreadyFilteredAttribute)
      filterChain.doFilter(request, response)
    else
      filterOnlyOnce(request, response, filterChain)
  }

  def filterOnlyOnce(request: ServletRequest, response: ServletResponse, filterChain: FilterChain): Unit

  private lazy val attribute: String = s"${getClass.getName}.$OnlyOnceAttributePrefix"
}

object OncePerRequestFilter {
  val OnlyOnceAttributePrefix = "OnlyOnceExecuted"
}
