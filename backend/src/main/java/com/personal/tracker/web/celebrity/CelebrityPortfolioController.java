package com.personal.tracker.web.celebrity;

import com.personal.tracker.domain.celebrity.CelebrityHoldingChange;
import com.personal.tracker.domain.celebrity.CelebrityInvestorOverview;
import com.personal.tracker.domain.celebrity.CelebritySyncStatus;
import com.personal.tracker.domain.celebrity.alerts.CelebrityAlertSettings;
import com.personal.tracker.domain.celebrity.discovery.CelebrityDiscoveryModels.Consensus;
import com.personal.tracker.domain.celebrity.discovery.CelebrityDiscoveryModels.FeedItem;
import com.personal.tracker.domain.celebrity.discovery.CelebrityDiscoveryModels.InstrumentOwnership;
import com.personal.tracker.domain.celebrity.discovery.CelebrityDiscoveryModels.WatchlistOverlap;
import com.personal.tracker.service.celebrity.CelebrityDiscoveryService;
import com.personal.tracker.service.celebrity.CelebrityPortfolioService;
import com.personal.tracker.service.celebrity.CelebrityPortfolioService.CelebrityPortfolio;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/celebrity")
public class CelebrityPortfolioController {
  private final CelebrityPortfolioService service;
  private final CelebrityDiscoveryService discovery;

  public CelebrityPortfolioController(CelebrityPortfolioService service, CelebrityDiscoveryService discovery) {
    this.service = service;
    this.discovery = discovery;
  }

  @GetMapping("/investors")
  List<CelebrityInvestorOverview> investors() {
    return service.investors();
  }

  @GetMapping("/investors/{slug}/holdings")
  CelebrityPortfolio holdings(
      @PathVariable String slug,
      @RequestParam(defaultValue = "80") int limit) {
    return service.holdings(slug, limit);
  }

  @GetMapping("/investors/{slug}/changes")
  List<CelebrityHoldingChange> changes(@PathVariable String slug) {
    return service.changes(slug);
  }

  @GetMapping("/feed")
  List<FeedItem> feed(@RequestParam(defaultValue = "40") int limit) {
    return discovery.feed(limit);
  }

  @GetMapping("/consensus")
  List<Consensus> consensus(@RequestParam(defaultValue = "30") int limit) {
    return discovery.consensus(limit);
  }

  @GetMapping("/instruments/{symbol}")
  List<InstrumentOwnership> ownership(@PathVariable String symbol) {
    return discovery.ownership(symbol);
  }

  @GetMapping("/watchlist-overlap")
  List<WatchlistOverlap> watchlistOverlap(
      @RequestParam(required = false) String kolId,
      @RequestParam(defaultValue = "24") int limit) {
    return discovery.watchlistOverlap(kolId, limit);
  }

  @GetMapping("/sync-status")
  CelebritySyncStatus syncStatus() {
    return service.syncStatus();
  }

  @GetMapping("/alert-settings")
  CelebrityAlertSettings alertSettings() {
    return service.alertSettings();
  }

  @PutMapping("/alert-settings")
  CelebrityAlertSettings saveAlertSettings(@RequestBody AlertSettingsRequest request) {
    return service.saveAlertSettings(request.enabled(), request.investorSlugs(), request.minimumReportedWeight());
  }

  @PostMapping("/sync")
  CelebritySyncStatus sync() {
    return service.syncAsync("手动刷新");
  }

  private record AlertSettingsRequest(
      boolean enabled,
      List<String> investorSlugs,
      BigDecimal minimumReportedWeight) {
  }
}
