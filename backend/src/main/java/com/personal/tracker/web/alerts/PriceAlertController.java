package com.personal.tracker.web.alerts;

import com.personal.tracker.repository.alerts.PriceAlertRepository.PriceAlertView;
import com.personal.tracker.service.alerts.PriceAlertMonitor.MonitorStatus;
import com.personal.tracker.service.alerts.PriceAlertService;
import com.personal.tracker.service.alerts.PriceAlertService.CreateCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/price-alerts")
public class PriceAlertController {
  private final PriceAlertService alerts;

  public PriceAlertController(PriceAlertService alerts) {
    this.alerts = alerts;
  }

  @GetMapping
  List<PriceAlertView> list() {
    return alerts.list();
  }

  @GetMapping("/status")
  MonitorStatus status() {
    return alerts.status();
  }

  @PostMapping
  PriceAlertView create(@RequestBody CreateRequest request) {
    return alerts.create(new CreateCommand(
        request.symbol(), request.alertType(), request.lowerPrice(),
        request.upperPrice(), request.targetPrice()));
  }

  @PutMapping("/{id}")
  PriceAlertView update(@PathVariable String id, @RequestBody CreateRequest request) {
    return alerts.update(id, new CreateCommand(
        request.symbol(), request.alertType(), request.lowerPrice(),
        request.upperPrice(), request.targetPrice()));
  }

  @PutMapping("/{id}/active")
  PriceAlertView setActive(@PathVariable String id, @RequestBody ActiveRequest request) {
    return alerts.setEnabled(id, request.enabled());
  }

  @DeleteMapping("/{id}")
  Map<String, String> delete(@PathVariable String id) {
    alerts.delete(id);
    return Map.of("status", "DELETED");
  }

  public record CreateRequest(
      String symbol,
      String alertType,
      BigDecimal lowerPrice,
      BigDecimal upperPrice,
      BigDecimal targetPrice) {
  }

  public record ActiveRequest(boolean enabled) {
  }
}
