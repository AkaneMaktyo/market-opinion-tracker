package com.personal.tracker.service.alerts;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.alerts.PriceAlertRepository;
import com.personal.tracker.repository.alerts.PriceAlertRepository.PriceAlertView;
import com.personal.tracker.service.market.BitgetMarketBarProvider;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PriceAlertService {
  private final PriceAlertRepository alerts;
  private final InstrumentRepository instruments;
  private final BitgetMarketBarProvider bitget;
  private final PriceAlertMonitor monitor;

  public PriceAlertService(
      PriceAlertRepository alerts,
      InstrumentRepository instruments,
      BitgetMarketBarProvider bitget,
      PriceAlertMonitor monitor) {
    this.alerts = alerts;
    this.instruments = instruments;
    this.bitget = bitget;
    this.monitor = monitor;
  }

  public List<PriceAlertView> list() {
    return alerts.list();
  }

  public PriceAlertView create(CreateCommand command) {
    String symbol = command.symbol() == null ? "" : command.symbol().trim().toUpperCase();
    AlertValues values = validate(command.alertType(), symbol, command.lowerPrice(),
        command.upperPrice(), command.targetPrice());
    Instrument instrument = instruments.findBySymbol(symbol)
        .orElseThrow(() -> new IllegalArgumentException("标的不存在，请先把它加入标的列表：" + symbol));
    Instrument mapped = ensureBitgetMapping(instrument);
    PriceAlertView created = alerts.create(
        mapped.id(),
        values.alertType(),
        values.lower(),
        values.upper(),
        values.target());
    monitor.refreshNow();
    return created;
  }

  public PriceAlertView update(String id, CreateCommand command) {
    String symbol = command.symbol() == null ? "" : command.symbol().trim().toUpperCase();
    AlertValues values = validate(command.alertType(), symbol, command.lowerPrice(),
        command.upperPrice(), command.targetPrice());
    Instrument instrument = instruments.findBySymbol(symbol)
        .orElseThrow(() -> new IllegalArgumentException("标的不存在，请先把它加入标的列表：" + symbol));
    Instrument mapped = ensureBitgetMapping(instrument);
    PriceAlertView updated = alerts.update(
        id,
        mapped.id(),
        values.alertType(),
        values.lower(),
        values.upper(),
        values.target())
        .orElseThrow(() -> new IllegalArgumentException("价格提醒不存在"));
    monitor.reset(id);
    return updated;
  }

  public PriceAlertView setEnabled(String id, boolean enabled) {
    PriceAlertView updated = alerts.setEnabled(id, enabled)
        .orElseThrow(() -> new IllegalArgumentException("价格提醒不存在"));
    if (enabled) {
      monitor.reset(id);
    } else {
      monitor.refreshNow();
    }
    return updated;
  }

  public void delete(String id) {
    if (!alerts.delete(id)) {
      throw new IllegalArgumentException("价格提醒不存在");
    }
    monitor.refreshNow();
  }

  public PriceAlertMonitor.MonitorStatus status() {
    return monitor.status();
  }

  private Instrument ensureBitgetMapping(Instrument instrument) {
    if (present(instrument.bitgetCategory()) && present(instrument.bitgetSymbol())) {
      return instrument;
    }
    bitget.fetch(instrument, "1H", null, null, 1);
    Instrument refreshed = instruments.findById(instrument.id()).orElse(instrument);
    if (!present(refreshed.bitgetCategory()) || !present(refreshed.bitgetSymbol())) {
      throw new IllegalArgumentException(
          "Bitget 暂未找到 " + instrument.symbol() + " 的可用现货、合约或 RWA 行情，无法创建后台提醒");
    }
    return refreshed;
  }

  private static AlertValues validate(
      String alertType,
      String symbol,
      BigDecimal lower,
      BigDecimal upper,
      BigDecimal target) {
    if (symbol.isBlank()) {
      throw new IllegalArgumentException("请先指定标的");
    }
    String type = "POINT".equalsIgnoreCase(alertType) ? "POINT" : "RANGE";
    if ("POINT".equals(type)) {
      if (target == null || target.signum() <= 0) {
        throw new IllegalArgumentException("提醒点位必须大于 0");
      }
      BigDecimal value = target.stripTrailingZeros();
      return new AlertValues(type, value, value, value);
    }
    if (lower == null || upper == null) {
      throw new IllegalArgumentException("请填写完整的价格区间");
    }
    if (lower.signum() <= 0 || upper.signum() <= 0) {
      throw new IllegalArgumentException("价格必须大于 0");
    }
    if (lower.compareTo(upper) > 0) {
      throw new IllegalArgumentException("区间下限不能高于上限");
    }
    return new AlertValues(type, lower.stripTrailingZeros(), upper.stripTrailingZeros(), null);
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  public record CreateCommand(
      String symbol,
      String alertType,
      BigDecimal lowerPrice,
      BigDecimal upperPrice,
      BigDecimal targetPrice) {
  }

  private record AlertValues(
      String alertType,
      BigDecimal lower,
      BigDecimal upper,
      BigDecimal target) {
  }
}
