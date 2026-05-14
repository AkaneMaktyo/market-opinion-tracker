package com.personal.tracker.service;

import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.repository.SessionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataService implements ApplicationRunner {
  private final SessionRepository sessions;
  private final OpinionService opinions;
  private final MarketDataService marketData;

  public DemoDataService(
      SessionRepository sessions,
      OpinionService opinions,
      MarketDataService marketData) {
    this.sessions = sessions;
    this.opinions = opinions;
    this.marketData = marketData;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!sessions.findRecent("default", 1).isEmpty()) {
      return;
    }
    var session = sessions.create(
        "default",
        LocalDate.now().toString(),
        "示例直播",
        "Codex Demo",
        "NVDA：回踩关键支撑后仍偏强，突破前高可以继续看多。");
    opinions.create(new OpinionService.CreateOpinionCommand(
        session.id(),
        "NVDA",
        "NVIDIA",
        "AI",
        "BULLISH",
        "短线",
        "回踩不破支撑，资金仍在高景气方向。",
        "放量突破前高",
        "跌破支撑位后观点失效",
        80,
        "回踩关键支撑后仍偏强，突破前高可以继续看多。",
        BigDecimal.valueOf(865.20),
        "看多",
        "跌破支撑位后观点失效",
        "AI 资金主线",
        "830 支撑\n910 目标",
        "{}",
        LocalDateTime.now().minusDays(3).toString(),
        List.of(
            new PriceLevel(null, null, "SUPPORT", BigDecimal.valueOf(830), "关键支撑"),
            new PriceLevel(null, null, "TARGET", BigDecimal.valueOf(910), "第一目标"))));
    marketData.barsForSymbol("NVDA", "1D");
  }
}
