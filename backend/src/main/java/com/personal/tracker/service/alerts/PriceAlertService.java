package com.personal.tracker.service.alerts;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.alerts.PriceAlertRepository;
import com.personal.tracker.repository.alerts.PriceAlertRepository.PriceAlertView;
import com.personal.tracker.service.alerts.recognition.MessagePriceAlertRecognitionService;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Candidate;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Result;
import com.personal.tracker.service.market.BitgetMarketBarProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PriceAlertService {
  private final PriceAlertRepository alerts;
  private final InstrumentRepository instruments;
  private final BitgetMarketBarProvider bitget;
  private final PriceAlertMonitor monitor;
  private final MessagePriceAlertRecognitionService recognitions;

  public PriceAlertService(
      PriceAlertRepository alerts,
      InstrumentRepository instruments,
      BitgetMarketBarProvider bitget,
      PriceAlertMonitor monitor,
      MessagePriceAlertRecognitionService recognitions) {
    this.alerts = alerts;
    this.instruments = instruments;
    this.bitget = bitget;
    this.monitor = monitor;
    this.recognitions = recognitions;
  }

  public List<PriceAlertView> list() {
    return alerts.list();
  }

  public PriceAlertView create(CreateCommand command) {
    String symbol = symbol(command.symbol());
    AlertValues values = validate(
        command.alertType(), command.triggerDirection(), symbol,
        command.lowerPrice(), command.upperPrice(), command.targetPrice());
    Instrument instrument = instruments.findBySymbol(symbol)
        .orElseThrow(() -> new IllegalArgumentException("标的不存在，请先把它加入标的列表：" + symbol));
    Instrument mapped = ensureBitgetMapping(instrument);
    PriceAlertView created = alerts.create(
        mapped.id(), values.alertType(), values.triggerDirection(), values.lower(),
        values.upper(), values.target(), null, null);
    monitor.refreshNow();
    return created;
  }

  public PriceAlertView update(String id, CreateCommand command) {
    String symbol = symbol(command.symbol());
    AlertValues values = validate(
        command.alertType(), command.triggerDirection(), symbol,
        command.lowerPrice(), command.upperPrice(), command.targetPrice());
    Instrument instrument = instruments.findBySymbol(symbol)
        .orElseThrow(() -> new IllegalArgumentException("标的不存在，请先把它加入标的列表：" + symbol));
    Instrument mapped = ensureBitgetMapping(instrument);
    PriceAlertView updated = alerts.update(
        id, mapped.id(), values.alertType(), values.triggerDirection(),
        values.lower(), values.upper(), values.target())
        .orElseThrow(() -> new IllegalArgumentException("价格提醒不存在"));
    monitor.reset(id);
    return updated;
  }

  public BatchResult createBatch(String recognitionId, String kolId, List<BatchItem> items) {
    Result recognition = recognitions.require(recognitionId);
    if (!"SUCCESS".equals(recognition.status())) {
      throw new IllegalArgumentException("智能识别结果不可用于创建提醒");
    }
    List<BatchItemResult> results = new ArrayList<>();
    for (BatchItem item : items == null ? List.<BatchItem>of() : items) {
      try {
        results.add(createRecognized(recognition, kolId, item));
      } catch (RuntimeException error) {
        results.add(new BatchItemResult(
            item == null ? "" : text(item.candidateId()), "FAILED", null, message(error)));
      }
    }
    if (results.stream().anyMatch(item -> "CREATED".equals(item.status()))) {
      monitor.refreshNow();
    }
    return new BatchResult(recognitionId, results);
  }

  private BatchItemResult createRecognized(Result recognition, String kolId, BatchItem item) {
    if (item == null) throw new IllegalArgumentException("候选不能为空");
    Candidate source = recognition.candidates().stream()
        .filter(candidate -> candidate.candidateId().equals(text(item.candidateId())))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("候选不属于该识别结果"));
    var existingSource = alerts.findBySource(recognition.recognitionId(), source.candidateId());
    if (existingSource.isPresent()) {
      return result(source, "EXISTS", existingSource.get(), "该候选已创建");
    }
    String selectedSymbol = symbol(selected(item.symbol(), source.symbol()));
    String name = selected(item.instrumentName(), source.instrumentName());
    String market = selected(item.market(), source.market()).toUpperCase(Locale.ROOT);
    AlertValues values = validate(
        selected(item.alertType(), source.alertType()),
        selected(item.triggerDirection(), source.triggerDirection()),
        selectedSymbol, item.lowerPrice(), item.upperPrice(), item.targetPrice());
    Instrument instrument = instruments.findBySymbol(selectedSymbol)
        .orElseGet(() -> instruments.saveIfAbsent(selectedSymbol, name, market, null));
    Instrument mapped = ensureBitgetMapping(instrument);
    instruments.setWatchlist(kolId, mapped.id(), true);
    var equivalent = alerts.findEquivalent(
        mapped.id(), values.alertType(), values.triggerDirection(),
        values.lower(), values.upper(), values.target());
    if (equivalent.isPresent()) {
      return result(source, "EXISTS", equivalent.get(), "相同提醒已存在");
    }
    PriceAlertView created;
    try {
      created = alerts.create(
          mapped.id(), values.alertType(), values.triggerDirection(), values.lower(),
          values.upper(), values.target(), recognition.recognitionId(), source.candidateId());
    } catch (DuplicateKeyException duplicate) {
      PriceAlertView concurrent = alerts.findBySource(
          recognition.recognitionId(), source.candidateId()).orElseThrow(() -> duplicate);
      return result(source, "EXISTS", concurrent, "该候选已由另一请求创建");
    }
    return result(source, "CREATED", created, "价格提醒已创建");
  }

  private BatchItemResult result(
      Candidate source, String status, PriceAlertView alert, String message) {
    return new BatchItemResult(source.candidateId(), status, alert, message);
  }

  public PriceAlertView setEnabled(String id, boolean enabled) {
    PriceAlertView updated = alerts.setEnabled(id, enabled)
        .orElseThrow(() -> new IllegalArgumentException("价格提醒不存在"));
    if (enabled) monitor.reset(id); else monitor.refreshNow();
    return updated;
  }

  public void delete(String id) {
    if (!alerts.delete(id)) throw new IllegalArgumentException("价格提醒不存在");
    monitor.refreshNow();
  }

  public PriceAlertMonitor.MonitorStatus status() {
    return monitor.status();
  }

  private Instrument ensureBitgetMapping(Instrument instrument) {
    if (present(instrument.bitgetCategory()) && present(instrument.bitgetSymbol())) return instrument;
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
      String triggerDirection,
      String symbol,
      BigDecimal lower,
      BigDecimal upper,
      BigDecimal target) {
    if (symbol.isBlank()) throw new IllegalArgumentException("请先指定标的");
    String type = "POINT".equalsIgnoreCase(alertType) ? "POINT" : "RANGE";
    String direction = normalizeDirection(type, triggerDirection);
    if ("POINT".equals(type)) {
      if (target == null || target.signum() <= 0) {
        throw new IllegalArgumentException("提醒点位必须大于 0");
      }
      BigDecimal value = target.stripTrailingZeros();
      return new AlertValues(type, direction, value, value, value);
    }
    if (lower == null || upper == null) throw new IllegalArgumentException("请填写完整的价格区间");
    if (lower.signum() <= 0 || upper.signum() <= 0) {
      throw new IllegalArgumentException("价格必须大于 0");
    }
    if (lower.compareTo(upper) > 0) throw new IllegalArgumentException("区间下限不能高于上限");
    return new AlertValues(
        type, "ANY", lower.stripTrailingZeros(), upper.stripTrailingZeros(), null);
  }

  private static String normalizeDirection(String alertType, String value) {
    if (!"POINT".equals(alertType)) return "ANY";
    String normalized = text(value).toUpperCase(Locale.ROOT);
    return List.of("ANY", "UP", "DOWN").contains(normalized) ? normalized : "ANY";
  }

  private static String symbol(String value) {
    return text(value).toUpperCase(Locale.ROOT);
  }

  private static String selected(String value, String fallback) {
    return value == null || value.isBlank() ? text(fallback) : value.trim();
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }

  private static String message(Throwable error) {
    return error.getMessage() == null || error.getMessage().isBlank()
        ? error.getClass().getSimpleName() : error.getMessage();
  }

  public record CreateCommand(
      String symbol,
      String alertType,
      BigDecimal lowerPrice,
      BigDecimal upperPrice,
      BigDecimal targetPrice,
      String triggerDirection) {
  }

  public record BatchItem(
      String candidateId,
      String instrumentName,
      String symbol,
      String market,
      String alertType,
      String triggerDirection,
      BigDecimal lowerPrice,
      BigDecimal upperPrice,
      BigDecimal targetPrice) {
  }

  public record BatchResult(String recognitionId, List<BatchItemResult> items) {
  }

  public record BatchItemResult(
      String candidateId,
      String status,
      PriceAlertView alert,
      String message) {
  }

  private record AlertValues(
      String alertType,
      String triggerDirection,
      BigDecimal lower,
      BigDecimal upper,
      BigDecimal target) {
  }
}
