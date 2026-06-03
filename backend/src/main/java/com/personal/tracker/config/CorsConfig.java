package com.personal.tracker.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
  private static final List<String> DEFAULT_ORIGIN_PATTERNS = Stream.concat(
      Stream.of(
          "http://localhost:*",
          "https://localhost:*",
          "http://127.0.0.1:*",
          "https://127.0.0.1:*",
          "http://0.0.0.0:*",
          "https://0.0.0.0:*",
          "http://[::1]:*",
          "https://[::1]:*",
          "http://10.*.*.*:*",
          "https://10.*.*.*:*",
          "http://192.168.*.*:*",
          "https://192.168.*.*:*"),
      IntStream.rangeClosed(16, 31)
          .boxed()
          .flatMap(i -> Stream.of(
              "http://172." + i + ".*.*:*",
              "https://172." + i + ".*.*:*")))
      .toList();

  private final String configuredOriginPatterns;

  public CorsConfig(@Value("${app.cors.allowed-origin-patterns:}") String configuredOriginPatterns) {
    this.configuredOriginPatterns = configuredOriginPatterns;
  }

  @Bean
  WebMvcConfigurer corsConfigurer() {
    String[] allowedOriginPatterns = allowedOriginPatterns(configuredOriginPatterns);
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(allowedOriginPatterns)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false);
      }
    };
  }

  static String[] allowedOriginPatterns(String configuredOriginPatterns) {
    return Stream.concat(
            DEFAULT_ORIGIN_PATTERNS.stream(),
            Arrays.stream(configuredOriginPatterns.split(",")))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .distinct()
        .toArray(String[]::new);
  }
}
