package com.personal.tracker.service.celebrity;

import com.personal.tracker.domain.celebrity.CelebrityHoldingChange;
import com.personal.tracker.domain.celebrity.CelebrityInvestor;
import com.personal.tracker.domain.celebrity.alerts.CelebrityAlertSettings;
import com.personal.tracker.repository.celebrity.CelebrityPortfolioRepository;
import com.personal.tracker.service.notify.WxPusherPushClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CelebrityDisclosureAlertService {
  private static final Logger log = LoggerFactory.getLogger(CelebrityDisclosureAlertService.class);
  private final CelebrityPortfolioRepository repository;
  private final CelebrityDiscoveryService discovery;
  private final WxPusherPushClient pushClient;

  public CelebrityDisclosureAlertService(
      CelebrityPortfolioRepository repository,
      CelebrityDiscoveryService discovery,
      WxPusherPushClient pushClient) {
    this.repository = repository;
    this.discovery = discovery;
    this.pushClient = pushClient;
  }

  public void observe(List<CelebrityInvestor> investors, Set<String> baselineInvestorIds) {
    CelebrityAlertSettings settings = repository.alertSettings();
    Set<String> selected = Set.copyOf(settings.investorSlugs());
    for (CelebrityInvestor investor : investors) {
      boolean established = baselineInvestorIds.contains(investor.id());
      for (CelebrityHoldingChange change : discovery.changes(investor.slug())) {
        repository.claimDisclosureAlert(investor.id(), filingId(investor.id()), change.holdingKey(), change.action())
            .ifPresent(alertId -> process(alertId, investor, change, settings, selected, established));
      }
    }
  }

  private void process(
      String alertId,
      CelebrityInvestor investor,
      CelebrityHoldingChange change,
      CelebrityAlertSettings settings,
      Set<String> selected,
      boolean established) {
    String skipped = skipReason(investor, change, settings, selected, established);
    if (!skipped.isBlank()) {
      repository.finishDisclosureAlert(alertId, "SKIPPED", skipped);
      return;
    }
    WxPusherPushClient.PushResult result = pushClient.send(title(investor, change), content(investor, change), "CELEBRITY");
    repository.finishDisclosureAlert(alertId, result.ok() ? "SENT" : result.status(), result.error());
    if (!result.ok()) {
      log.warn("名人披露提醒未发送 investor={} action={} reason={}", investor.slug(), change.action(), result.error());
    }
  }

  private String skipReason(
      CelebrityInvestor investor,
      CelebrityHoldingChange change,
      CelebrityAlertSettings settings,
      Set<String> selected,
      boolean established) {
    if (!established) {
      return "首次同步仅建立提醒基线";
    }
    if (!settings.enabled()) {
      return "名人持仓提醒默认关闭";
    }
    if (!selected.contains(investor.slug())) {
      return "该投资人未被选择提醒";
    }
    BigDecimal reportedWeight = change.reportedWeight() == null ? BigDecimal.ZERO : change.reportedWeight();
    if (reportedWeight.compareTo(settings.minimumReportedWeight()) < 0) {
      return "报告占比低于提醒阈值";
    }
    return "";
  }

  private String filingId(String investorId) {
    return repository.latestFiling(investorId).map(item -> item.id()).orElse("");
  }

  private static String title(CelebrityInvestor investor, CelebrityHoldingChange change) {
    return "名人持仓 · " + investor.displayName() + actionLabel(change.action()) + " " + security(change);
  }

  private static String content(CelebrityInvestor investor, CelebrityHoldingChange change) {
    return "公开披露变动（只读提示，不构成交易指令）\n"
        + "投资人：" + investor.displayName() + "\n"
        + "标的：" + security(change) + "\n"
        + "动作：" + actionLabel(change.action()) + "\n"
        + "报告期：" + change.reportDate() + "\n"
        + "报告占比：" + percent(change.reportedWeight()) + "\n"
        + "来源：" + change.sourceUrl();
  }

  private static String security(CelebrityHoldingChange change) {
    String symbol = change.symbol() == null || change.symbol().isBlank() ? change.issuerName() : change.symbol();
    return symbol == null ? "待映射标的" : symbol;
  }

  private static String actionLabel(String action) {
    return switch (action == null ? "" : action.toUpperCase(Locale.ROOT)) {
      case "NEW" -> "新增";
      case "ADDED" -> "加仓";
      case "REDUCED" -> "减仓";
      case "EXITED" -> "退出";
      default -> "变动";
    };
  }

  private static String percent(BigDecimal value) {
    return value == null ? "--" : value.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
  }
}
