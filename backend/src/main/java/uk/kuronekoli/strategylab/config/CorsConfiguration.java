package uk.kuronekoli.strategylab.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfiguration implements WebMvcConfigurer {
  private final String[] allowedOrigins;
  public CorsConfiguration(@Value("${app.cors.allowed-origin-patterns}") String patterns) {
    allowedOrigins = Arrays.stream(patterns.split(",")).map(String::trim).filter(s -> !s.isBlank()).toArray(String[]::new);
  }
  @Override public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**").allowedOriginPatterns(allowedOrigins).allowedMethods("GET", "POST", "OPTIONS").allowedHeaders("*").maxAge(3600);
  }
}
