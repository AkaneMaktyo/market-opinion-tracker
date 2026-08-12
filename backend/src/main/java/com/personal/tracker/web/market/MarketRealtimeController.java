package com.personal.tracker.web.market;

import com.personal.tracker.service.market.RealtimeMarketBarService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/market")
public class MarketRealtimeController {
  private final RealtimeMarketBarService realtime;

  public MarketRealtimeController(RealtimeMarketBarService realtime) {
    this.realtime = realtime;
  }

  @GetMapping(value = "/{symbol}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter stream(
      @PathVariable String symbol,
      @RequestParam(defaultValue = "1D") String timeframe,
      HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
    response.setHeader("X-Accel-Buffering", "no");
    return realtime.stream(symbol, timeframe);
  }
}
