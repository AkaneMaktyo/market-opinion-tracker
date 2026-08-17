package com.personal.tracker.web.trading;

import com.personal.tracker.service.trading.BitgetDemoClient;
import com.personal.tracker.service.trading.BitgetDemoClient.BitgetResponse;
import com.personal.tracker.service.trading.BitgetDemoClient.TradingStatus;
import com.personal.tracker.service.trading.futures.BitgetFuturesPositionService;
import com.personal.tracker.service.trading.futures.BitgetFuturesPositionService.FuturesPortfolio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading/bitget")
public class BitgetTradingController {
  private final BitgetDemoClient bitget;
  private final BitgetFuturesPositionService futures;

  public BitgetTradingController(
      BitgetDemoClient bitget,
      BitgetFuturesPositionService futures) {
    this.bitget = bitget;
    this.futures = futures;
  }

  @GetMapping("/status")
  TradingStatus status() {
    return bitget.status();
  }

  @GetMapping("/futures-portfolio")
  FuturesPortfolio futuresPortfolio() {
    return futures.portfolio();
  }

  @GetMapping("/accounts")
  BitgetResponse accounts() {
    return bitget.accounts();
  }

  @GetMapping("/positions")
  BitgetResponse positions() {
    return bitget.positions();
  }

  @GetMapping("/open-orders")
  BitgetResponse openOrders() {
    return bitget.openOrders();
  }
}
