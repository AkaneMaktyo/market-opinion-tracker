package com.personal.tracker.service.instruments;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.service.wxpusher.instruments.MessageInstrumentExtractor;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class InstrumentHistoryService {
  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
  private static final long CACHE_MS = 30_000;
  private final InstrumentRepository instruments;
  private final SessionRepository sessions;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public InstrumentHistoryService(InstrumentRepository instruments, SessionRepository sessions) {
    this.instruments = instruments;
    this.sessions = sessions;
  }

  public List<Instrument> findByKol(String kolId, String query) {
    String key = safe(kolId).trim() + "\n" + safe(query).trim().toUpperCase(Locale.ROOT);
    CacheEntry cached = cache.get(key);
    if (cached != null && System.currentTimeMillis() - cached.createdAt() < CACHE_MS) {
      return cached.items();
    }
    LinkedHashMap<String, Instrument> result = new LinkedHashMap<>();
    instruments.findOpinionHistoryByKol(kolId, query)
        .forEach(item -> result.put(item.symbol(), item));
    Map<String, Instrument> candidates = instruments.findAll(query).stream()
        .collect(java.util.stream.Collectors.toMap(Instrument::symbol, item -> item));
    sessions.findSince(kolId, YearMonth.now(APP_ZONE).atDay(1).toString()).forEach(session -> {
      String text = String.join("\n", safe(session.title()), safe(session.rawText()));
      MessageInstrumentExtractor.extract(text).stream()
          .map(symbol -> symbol.toUpperCase(Locale.ROOT))
          .map(candidates::get)
          .filter(java.util.Objects::nonNull)
          .forEach(item -> result.putIfAbsent(item.symbol(), item));
    });
    List<Instrument> items = List.copyOf(result.values());
    cache.put(key, new CacheEntry(items, System.currentTimeMillis()));
    return items;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private record CacheEntry(List<Instrument> items, long createdAt) {
  }
}
