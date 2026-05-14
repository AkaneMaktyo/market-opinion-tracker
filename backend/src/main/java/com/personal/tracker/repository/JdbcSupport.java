package com.personal.tracker.repository;

import java.time.Instant;
import java.util.UUID;

public final class JdbcSupport {
  private JdbcSupport() {
  }

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public static String now() {
    return Instant.now().toString();
  }

  public static String symbol(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }
}
