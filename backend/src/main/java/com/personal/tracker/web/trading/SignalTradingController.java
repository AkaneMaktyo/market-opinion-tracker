package com.personal.tracker.web.trading;

import com.personal.tracker.service.trading.spot.SignalTradePlanService;
import com.personal.tracker.service.trading.spot.SignalTradePlanService.CreatePlanCommand;
import com.personal.tracker.service.trading.spot.SignalTradePlanService.TradePlanView;
import com.personal.tracker.service.trading.spot.SignalTradePlanService.TradingStatus;
import com.personal.tracker.service.trading.spot.SpotPositionService;
import com.personal.tracker.service.trading.spot.SpotPositionService.PositionPortfolio;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading/signals")
public class SignalTradingController {
  private final SignalTradePlanService plans;
  private final SpotPositionService positions;

  public SignalTradingController(SignalTradePlanService plans, SpotPositionService positions) {
    this.plans = plans;
    this.positions = positions;
  }

  @GetMapping("/status")
  public TradingStatus status() {
    return plans.status();
  }

  @GetMapping("/plans")
  public List<TradePlanView> plans() {
    return plans.plans();
  }

  @PutMapping("/alerts/{alertId}/plan")
  public TradePlanView create(
      @PathVariable String alertId,
      @RequestBody CreatePlanCommand command) {
    return plans.create(alertId, command);
  }

  @GetMapping("/positions")
  public PositionPortfolio positions(
      @RequestParam(defaultValue = "false") boolean refresh) {
    return positions.positions(refresh);
  }

  @PutMapping("/positions/{provider}/{symbol}/cost")
  public PositionPortfolio setAverageCost(
      @PathVariable String provider,
      @PathVariable String symbol,
      @RequestBody PositionCostCommand command) {
    return positions.setAverageCost(provider, symbol,
        command == null ? null : command.averageCost());
  }

  @DeleteMapping("/positions/{provider}/{symbol}/cost")
  public PositionPortfolio clearAverageCost(
      @PathVariable String provider,
      @PathVariable String symbol) {
    return positions.clearAverageCost(provider, symbol);
  }

  public record PositionCostCommand(BigDecimal averageCost) {
  }
}
