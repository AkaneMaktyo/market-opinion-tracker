package com.personal.tracker.web.trading;

import com.personal.tracker.service.trading.BitgetDemoClient;
import com.personal.tracker.service.trading.BitgetDemoClient.BitgetResponse;
import com.personal.tracker.service.trading.BitgetDemoClient.TradingStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading/bitget")
public class BitgetTradingController {
  private final BitgetDemoClient bitget;

  public BitgetTradingController(BitgetDemoClient bitget) {
    this.bitget = bitget;
  }

  @GetMapping("/status")
  TradingStatus status() {
    return bitget.status();
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
