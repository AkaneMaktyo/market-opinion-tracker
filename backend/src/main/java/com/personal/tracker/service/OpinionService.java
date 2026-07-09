package com.personal.tracker.service;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.domain.Opinion;
import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.domain.Review;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.OpinionRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.service.positions.KolPositionService;
import com.personal.tracker.service.resonance.ResonanceService;
import com.personal.tracker.service.wxpusher.WxPusherBloggerMatcher;
import com.personal.tracker.service.wxpusher.WxPusherClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpinionService {
  private static final Logger log = LoggerFactory.getLogger(OpinionService.class);
  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
  private static final int MESSAGE_LOOKBACK_LIMIT = 800;
  private final InstrumentRepository instruments;
  private final OpinionRepository opinions;
  private final SessionRepository sessions;
  private final WxPusherBloggerRepository wxpusherBloggers;
  private final WxPusherMessageRepository wxpusherMessages;
  private final KolPositionService positions;
  private final ResonanceService resonance;

  public OpinionService(
      InstrumentRepository instruments,
      OpinionRepository opinions,
      SessionRepository sessions,
      WxPusherBloggerRepository wxpusherBloggers,
      WxPusherMessageRepository wxpusherMessages,
      KolPositionService positions,
      ResonanceService resonance) {
    this.instruments = instruments;
    this.opinions = opinions;
    this.sessions = sessions;
    this.wxpusherBloggers = wxpusherBloggers;
    this.wxpusherMessages = wxpusherMessages;
    this.positions = positions;
    this.resonance = resonance;
  }

  @Transactional
  public OpinionView create(CreateOpinionCommand command) {
    Instrument instrument = instruments.saveIfAbsent(
        command.symbol(), command.instrumentName(), command.market(), command.sector());
    Opinion saved = opinions.create(new Opinion(
        null,
        command.sessionId(),
        instrument.id(),
        instrument.symbol(),
        value(command.direction(), "WATCH"),
        value(command.horizon(), "短线"),
        value(command.thesis(), "待补充"),
        command.triggerCondition(),
        command.invalidation(),
        command.confidence(),
        command.sourceQuote(),
        command.referencePrice(),
        command.rawDirection(),
        command.risksText(),
        command.catalystsText(),
        command.priceNotesText(),
        command.rawItemJson(),
        value(command.opinionTime(), LocalDateTime.now().toString()),
        "ACTIVE",
        null));
    opinions.replaceLevels(saved.id(), command.priceLevels());
    positions.apply(sessionKol(command.sessionId()), instrument, saved.id(), command.positionAction());
    refreshResonance(saved.symbol());
    return view(saved);
  }

  public List<OpinionView> find(String kolId, String symbol, String status, int limit) {
    String safeKol = kolId == null || kolId.isBlank() ? KolRepository.DEFAULT_ID : kolId;
    boolean onlyMessages = "MESSAGE".equalsIgnoreCase(status);
    List<OpinionView> result = new ArrayList<>();
    if (!onlyMessages) {
      result.addAll(opinions.find(safeKol, symbol, status, limit).stream().map(this::view).toList());
    }
    if (includeMessages(symbol, status)) {
      result.addAll(messageViews(safeKol, JdbcSupport.symbol(symbol)));
    }
    return result.stream()
        .sorted(Comparator.comparing((OpinionView item) -> item.opinion().opinionTime()).reversed())
        .limit(Math.max(1, Math.min(limit, 300)))
        .toList();
  }

  public OpinionView review(String opinionId, ReviewCommand command) {
    opinions.saveReview(new Review(
        null,
        opinionId,
        value(command.outcome(), "PENDING"),
        command.notes(),
        command.resultPrice(),
        value(command.reviewDate(), LocalDate.now().toString()),
        null));
    Opinion opinion = opinions.findById(opinionId)
        .orElseThrow(() -> new IllegalArgumentException("观点不存在"));
    return view(opinion);
  }

  private OpinionView view(Opinion opinion) {
    return new OpinionView(
        opinion,
        opinions.findLevels(opinion.id()),
        opinions.findReview(opinion.id()).orElse(null));
  }

  private List<OpinionView> messageViews(String kolId, String symbol) {
    Instrument instrument = instruments.findBySymbol(symbol).orElse(null);
    String name = instrument == null ? "" : instrument.name();
    String instrumentId = instrument == null ? "" : instrument.id();
    WxPusherBlogger blogger = configuredBlogger(kolId);
    return wxpusherMessages.findByKolSince(kolId, monthStart(), MESSAGE_LOOKBACK_LIMIT).stream()
        .filter(message -> matchesConfiguredBlogger(blogger, message))
        .filter(message -> mentions(messageText(message), symbol, name))
        .map(message -> messageView(message, instrumentId, symbol))
        .toList();
  }

  private WxPusherBlogger configuredBlogger(String kolId) {
    return wxpusherBloggers.enabled().stream()
        .filter(item -> item.kolId().equals(kolId))
        .findFirst()
        .orElse(null);
  }

  private boolean matchesConfiguredBlogger(WxPusherBlogger blogger, WxPusherMessage message) {
    if (blogger == null) {
      return true;
    }
    WxPusherClient.IncomingMessage incoming = new WxPusherClient.IncomingMessage(
        "stored",
        message.messageKey(),
        message.bloggerName(),
        message.title(),
        messageText(message),
        message.detailUrl(),
        message.sourceUrl(),
        message.messageTime(),
        "",
        message.rawPayloadJson(),
        0L);
    if (WxPusherBloggerMatcher.hasExplicitSource(incoming)) {
      return WxPusherBloggerMatcher.matches(incoming, blogger);
    }
    return true;
  }

  private OpinionView messageView(WxPusherMessage message, String instrumentId, String symbol) {
    String text = messageText(message);
    Opinion opinion = new Opinion(
        "wxmsg-" + message.id(),
        message.sessionId(),
        instrumentId,
        symbol,
        "WATCH",
        "消息",
        thesis(message, text, symbol),
        "",
        "",
        null,
        abbreviate(text, 900),
        null,
        "KOL消息",
        "",
        "",
        "",
        "",
        value(message.messageTime(), message.createdAt()),
        "MESSAGE",
        message.createdAt());
    return new OpinionView(opinion, List.of(), null);
  }

  private static boolean includeMessages(String symbol, String status) {
    return symbol != null && !symbol.isBlank()
        && (status == null || status.isBlank() || "MESSAGE".equalsIgnoreCase(status));
  }

  private static boolean mentions(String text, String symbol, String name) {
    if (text == null || text.isBlank() || symbol == null || symbol.isBlank()) {
      return false;
    }
    if (containsSymbol(text, symbol)) {
      return true;
    }
    return name != null && name.trim().length() >= 3 && text.contains(name.trim());
  }

  private static String thesis(WxPusherMessage message, String text, String symbol) {
    for (String line : text.split("\\R")) {
      String value = line.trim();
      if (meaningful(value) && containsSymbol(value, symbol)) {
        return abbreviate(value, 160);
      }
    }
    for (String line : text.split("\\R")) {
      String value = line.trim();
      if (meaningful(value)) {
        return abbreviate(value, 160);
      }
    }
    return abbreviate(value(message.summary(), message.title()), 160);
  }

  private static boolean containsSymbol(String text, String symbol) {
    Pattern pattern = Pattern.compile(
        "(?<![A-Z0-9])" + Pattern.quote(symbol.toUpperCase(Locale.ROOT)) + "(?![A-Z0-9])",
        Pattern.CASE_INSENSITIVE);
    return pattern.matcher(text).find();
  }

  private static boolean meaningful(String value) {
    return !value.isBlank()
        && !value.startsWith("您订阅的")
        && !"Embeds".equalsIgnoreCase(value)
        && !"图片".equals(value)
        && !"[图片]".equals(value);
  }

  private static String messageText(WxPusherMessage message) {
    return List.of(message.detailText(), message.summary(), message.title()).stream()
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse("");
  }

  private static String abbreviate(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    String compact = value.trim();
    return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "...";
  }

  private static String monthStart() {
    return YearMonth.now(APP_ZONE).atDay(1).toString();
  }

  private void refreshResonance(String symbol) {
    try {
      resonance.refreshForSymbol(symbol);
    } catch (RuntimeException error) {
      log.warn("刷新共振结果失败: {}", symbol, error);
    }
  }

  private String sessionKol(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return KolRepository.DEFAULT_ID;
    }
    return sessions.findById(sessionId).map(LiveSession::kolId).orElse(KolRepository.DEFAULT_ID);
  }

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  public record CreateOpinionCommand(
      String sessionId,
      String symbol,
      String instrumentName,
      String market,
      String sector,
      String direction,
      String positionAction,
      String horizon,
      String thesis,
      String triggerCondition,
      String invalidation,
      Integer confidence,
      String sourceQuote,
      BigDecimal referencePrice,
      String rawDirection,
      String risksText,
      String catalystsText,
      String priceNotesText,
      String rawItemJson,
      String opinionTime,
      List<PriceLevel> priceLevels) {
    public CreateOpinionCommand {
      symbol = JdbcSupport.symbol(symbol);
      market = JdbcSupport.market(market, symbol);
      priceLevels = priceLevels == null ? List.of() : priceLevels;
    }
  }

  public record ReviewCommand(
      String outcome,
      String notes,
      BigDecimal resultPrice,
      String reviewDate) {
  }

  public record OpinionView(
      Opinion opinion,
      List<PriceLevel> priceLevels,
      Review review) {
  }
}
