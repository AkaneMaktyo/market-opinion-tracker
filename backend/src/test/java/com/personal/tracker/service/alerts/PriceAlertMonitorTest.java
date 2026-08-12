package com.personal.tracker.service.alerts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PriceAlertMonitorTest {
  @Test
  void includesBothRangeBoundaries() {
    BigDecimal lower = new BigDecimal("100");
    BigDecimal upper = new BigDecimal("110");

    assertTrue(PriceAlertMonitor.inRange(new BigDecimal("100"), lower, upper));
    assertTrue(PriceAlertMonitor.inRange(new BigDecimal("105.5"), lower, upper));
    assertTrue(PriceAlertMonitor.inRange(new BigDecimal("110"), lower, upper));
    assertFalse(PriceAlertMonitor.inRange(new BigDecimal("99.99"), lower, upper));
    assertFalse(PriceAlertMonitor.inRange(new BigDecimal("110.01"), lower, upper));
  }

  @Test
  void detectsPointCrossingInEitherDirection() {
    BigDecimal target = new BigDecimal("100");

    assertTrue(PriceAlertMonitor.crossed(
        new BigDecimal("99"), new BigDecimal("101"), target));
    assertTrue(PriceAlertMonitor.crossed(
        new BigDecimal("101"), new BigDecimal("99"), target));
    assertTrue(PriceAlertMonitor.crossed(
        new BigDecimal("99"), new BigDecimal("100"), target));
    assertFalse(PriceAlertMonitor.crossed(
        new BigDecimal("98"), new BigDecimal("99"), target));
    assertFalse(PriceAlertMonitor.crossed(
        null, new BigDecimal("101"), target));
  }

  @Test
  void respectsDirectionalPointCrossing() {
    BigDecimal target = new BigDecimal("100");

    assertTrue(PriceAlertMonitor.crossed(
        new BigDecimal("99"), new BigDecimal("100"), target, "UP"));
    assertFalse(PriceAlertMonitor.crossed(
        new BigDecimal("101"), new BigDecimal("99"), target, "UP"));
    assertTrue(PriceAlertMonitor.crossed(
        new BigDecimal("101"), new BigDecimal("100"), target, "DOWN"));
    assertFalse(PriceAlertMonitor.crossed(
        new BigDecimal("99"), new BigDecimal("101"), target, "DOWN"));
  }
}
