package com.personal.tracker.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsConfigTest {
  @Test
  void includesLocalAndLanPatternsByDefault() {
    List<String> patterns = List.of(CorsConfig.allowedOriginPatterns(""));
    assertTrue(patterns.contains("http://localhost:*"));
    assertTrue(patterns.contains("http://127.0.0.1:*"));
    assertTrue(patterns.contains("http://192.168.*.*:*"));
    assertTrue(patterns.contains("http://172.31.*.*:*"));
  }

  @Test
  void appendsConfiguredPatternsOnce() {
    String[] patterns = CorsConfig.allowedOriginPatterns(
        " https://example.com , http://dev.box:4173, https://example.com ");
    String[] custom = List.of(patterns).stream()
        .filter(value -> value.contains("example.com") || value.contains("dev.box"))
        .toArray(String[]::new);
    assertArrayEquals(
        new String[] {"https://example.com", "http://dev.box:4173"},
        custom);
  }
}
