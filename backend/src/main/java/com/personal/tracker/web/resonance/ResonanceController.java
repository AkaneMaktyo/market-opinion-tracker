package com.personal.tracker.web.resonance;

import com.personal.tracker.service.resonance.ResonanceService;
import com.personal.tracker.service.resonance.ResonanceNotifier.AlertStatusView;
import com.personal.tracker.service.resonance.ResonanceService.ResonanceView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resonance")
public class ResonanceController {
  private final ResonanceService resonance;

  public ResonanceController(ResonanceService resonance) {
    this.resonance = resonance;
  }

  @GetMapping
  List<ResonanceView> list(
      @RequestParam(required = false) String symbol,
      @RequestParam(defaultValue = "20") int limit) {
    return resonance.list(symbol, limit);
  }

  @GetMapping("/status")
  AlertStatusView status() {
    return resonance.alertStatus();
  }

  @PostMapping("/refresh")
  List<ResonanceView> refresh(@RequestParam String symbol) {
    if (symbol == null || symbol.isBlank()) {
      throw new IllegalArgumentException("请先选择要刷新的标的");
    }
    return resonance.refreshForSymbol(symbol);
  }
}
