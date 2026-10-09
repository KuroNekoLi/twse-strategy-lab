package uk.kuronekoli.strategylab.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfiguration(@Value("\${app.cors.allowed-origin-patterns}") patterns: String) : WebMvcConfigurer {
  private val allowedOrigins = patterns.split(',').map(String::trim).filter(String::isNotBlank).toTypedArray()

  override fun addCorsMappings(registry: CorsRegistry) {
    registry.addMapping("/api/**").allowedOriginPatterns(*allowedOrigins)
      .allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("*").maxAge(3600)
  }
}
