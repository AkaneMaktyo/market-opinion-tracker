package com.personal.tracker.service.alerts.recognition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.WxPusherOcrProperties;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Candidate;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Result;
import com.personal.tracker.service.wxpusher.feed.WxPusherFeedService;
import com.personal.tracker.service.wxpusher.feed.WxPusherFeedService.FeedMessage;
import com.personal.tracker.service.wxpusher.ocr.ImageOcrClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MessagePriceAlertRecognitionService {
  private static final Pattern IMAGE = Pattern.compile(
      "(?m)^WXPUSHER_IMAGE_URL=((?:https?://|data:image/)[^\\r\\n]+)$");
  private static final Pattern OCR = Pattern.compile(
      "(?s)\\[图片转文字 \\d+]\\s*(.*?)\\s*\\[/图片转文字]");
  private static final String SYSTEM_PROMPT = """
      你是消息价格提醒识别器。只输出合法 json 对象，不要 Markdown 或解释。
      一条消息可能包含多个标的和多个价位，必须分别输出，不能只保留一个标的。
      输出格式：{"candidates":[{"instrumentName":"","symbol":"","market":"US|HK|CRYPTO|UNKNOWN","alertType":"POINT|RANGE","lowerPrice":null,"upperPrice":null,"targetPrice":null,"triggerDirection":"ANY|UP|DOWN","category":"SUPPORT|RESISTANCE|ENTRY|ADD|TAKE_PROFIT|STOP_LOSS|TARGET|CURRENT|HISTORICAL|OTHER","note":"","sourceQuote":"","source":"TEXT|OCR"}]}
      提取所有明确属于标的价格的数字，包括支撑、压力、入场、加仓、止盈、止损、目标、当前价和历史价。
      不要把年份、日期、时间、百分比、涨跌幅、仓位、杠杆倍数、宏观数据、预算、人数、营收或数量当成价格。
      “6.35万”转换为63500。区间写 lowerPrice/upperPrice，单点写 targetPrice。
      突破、涨破用 UP；跌破、下破、止损用 DOWN；到达、附近、支撑、压力及方向不明用 ANY。
      如果消息只给出大盘点位但没有指数代码，可结合“大盘、标普、纳指、道指”等上下文推断 SPX、NDX 或 DJI；无法判断属于哪个标的时不要输出该数字。
      中文名称尽量映射交易代码，例如谷歌 GOOGL、黄金 GOLD、白银 XAG、比特币 BTC、以太坊 ETH、原油 OIL。
      sourceQuote 必须逐字引用能支持该候选的短句；source 表明线索来自正文还是图片OCR。
      没有可用候选时输出 {"candidates":[]}。
      """;
  private static final Map<String, String> SYMBOL_ALIASES = Map.ofEntries(
      Map.entry("XAU", "GOLD"), Map.entry("GOOG", "GOOGL"),
      Map.entry("谷歌", "GOOGL"), Map.entry("黄金", "GOLD"),
      Map.entry("白银", "XAG"), Map.entry("比特币", "BTC"),
      Map.entry("以太坊", "ETH"), Map.entry("原油", "OIL"));
  private final WxPusherFeedService feed;
  private final ImageOcrClient ocrClient;
  private final WxPusherOcrProperties ocrProperties;
  private final PriceAlertRecognitionRepository repository;
  private final PriceAlertDeepSeekClient deepSeek;
  private final InstrumentRepository instruments;
  private final ObjectMapper mapper;

  public MessagePriceAlertRecognitionService(
      WxPusherFeedService feed,
      ImageOcrClient ocrClient,
      WxPusherOcrProperties ocrProperties,
      PriceAlertRecognitionRepository repository,
      PriceAlertDeepSeekClient deepSeek,
      InstrumentRepository instruments,
      ObjectMapper mapper) {
    this.feed = feed;
    this.ocrClient = ocrClient;
    this.ocrProperties = ocrProperties;
    this.repository = repository;
    this.deepSeek = deepSeek;
    this.instruments = instruments;
    this.mapper = mapper;
  }

  public Result recognize(String messageId) {
    return recognize(messageId, KolRepository.DEFAULT_ID);
  }

  public Result recognize(String messageId, String kolId) {
    Result existing = repository.find(messageId).orElse(null);
    if (existing != null && ("SUCCESS".equals(existing.status()) || "EMPTY".equals(existing.status()))) {
      addToWatchlist(kolId, existing.candidates(), null);
      return existing;
    }
    if (!repository.claim(messageId)) {
      return repository.find(messageId).orElseThrow();
    }
    List<String> warnings = new ArrayList<>();
    PreparedContent content = new PreparedContent("", "", false);
    try {
      FeedMessage message = feed.detail(messageId);
      content = prepare(message.detailText(), warnings);
      String primaryText = String.join("\n", clean(message.summary()), content.text()).trim();
      if (!hasSemanticContent(primaryText)
          || (content.hasImages() && content.ocrText().isBlank() && imagePlaceholder(primaryText))) {
        return repository.fail(
            messageId, content.ocrText(), "消息没有可识别的正文或图片文字", warnings);
      }
      String text = String.join("\n", clean(message.title()), primaryText).trim();
      String prompt = """
          消息ID：%s
          来源博主：%s
          正文和图片OCR：
          %s
          """.formatted(messageId, clean(message.bloggerName()), text);
      String output = deepSeek.recognize(messageId, SYSTEM_PROMPT, prompt);
      List<Candidate> candidates = parse(output);
      addToWatchlist(kolId, candidates, warnings);
      return repository.complete(messageId, content.ocrText(), candidates, warnings);
    } catch (Exception error) {
      return repository.fail(messageId, content.ocrText(), message(error), warnings);
    }
  }

  public Result require(String recognitionId) {
    return repository.requireById(recognitionId);
  }

  private void addToWatchlist(String kolId, List<Candidate> candidates, List<String> warnings) {
    Map<String, Candidate> symbols = new LinkedHashMap<>();
    candidates.forEach(candidate -> symbols.putIfAbsent(candidate.symbol(), candidate));
    symbols.values().forEach(candidate -> {
      try {
        Instrument instrument = instruments.findBySymbol(candidate.symbol())
            .orElseGet(() -> instruments.saveIfAbsent(
                candidate.symbol(), candidate.instrumentName(), candidate.market(), null));
        if (instrument != null) {
          instruments.setWatchlist(kolId, instrument.id(), true);
        }
      } catch (RuntimeException error) {
        if (warnings != null) {
          warnings.add(candidate.symbol() + " 自动加入自选表失败：" + message(error));
        }
      }
    });
  }

  private PreparedContent prepare(String raw, List<String> warnings) {
    String detail = clean(raw);
    List<String> ocrParts = new ArrayList<>();
    var existing = OCR.matcher(detail);
    while (existing.find()) {
      String text = clean(existing.group(1));
      if (!text.isBlank()) ocrParts.add(text);
    }
    var images = IMAGE.matcher(detail);
    int imageCount = 0;
    while (images.find()) {
      imageCount++;
      if (containsExistingOcrForImage(detail, imageCount)) continue;
      if (!ocrProperties.enabled()) {
        warnings.add("图片 " + imageCount + " 未识别：OCR 已禁用");
        continue;
      }
      if (imageCount > ocrProperties.maxImages()) {
        warnings.add("图片数量超过 OCR 上限 " + ocrProperties.maxImages());
        break;
      }
      try {
        String recognized = clean(ocrClient.recognize(images.group(1)));
        if (!recognized.isBlank()) ocrParts.add(recognized);
      } catch (RuntimeException error) {
        warnings.add("图片 " + imageCount + " OCR 失败：" + message(error));
      }
    }
    String body = IMAGE.matcher(OCR.matcher(detail).replaceAll("\n")).replaceAll("\n").trim();
    String ocrText = String.join("\n\n", ocrParts);
    String combined = body + (ocrText.isBlank() ? "" : "\n\n[图片OCR]\n" + ocrText);
    return new PreparedContent(combined.trim(), ocrText, imageCount > 0);
  }

  private boolean containsExistingOcrForImage(String detail, int imageIndex) {
    return detail.contains("[图片转文字 " + imageIndex + "]");
  }

  List<Candidate> parse(String output) {
    try {
      JsonNode root = mapper.readTree(stripFence(output));
      Map<String, Candidate> unique = new LinkedHashMap<>();
      int index = 0;
      for (JsonNode node : root.path("candidates")) {
        Candidate candidate = candidate(node, ++index);
        if (candidate == null) continue;
        unique.putIfAbsent(candidateKey(candidate), candidate);
      }
      return List.copyOf(unique.values());
    } catch (Exception error) {
      throw new IllegalStateException("DeepSeek 返回的候选 JSON 无效", error);
    }
  }

  private Candidate candidate(JsonNode node, int index) {
    String symbol = normalizeSymbol(node.path("symbol").asText(""), node.path("instrumentName").asText(""));
    String type = "RANGE".equalsIgnoreCase(node.path("alertType").asText()) ? "RANGE" : "POINT";
    BigDecimal lower = decimal(node.get("lowerPrice"));
    BigDecimal upper = decimal(node.get("upperPrice"));
    BigDecimal target = decimal(node.get("targetPrice"));
    if (symbol.isBlank()) return null;
    if ("RANGE".equals(type)) {
      if (!positive(lower) || !positive(upper)) return null;
      if (lower.compareTo(upper) > 0) {
        BigDecimal swap = lower; lower = upper; upper = swap;
      }
      target = null;
    } else {
      if (!positive(target)) return null;
      lower = target; upper = target;
    }
    String direction = value(node, "triggerDirection", "ANY").toUpperCase(Locale.ROOT);
    if (!List.of("ANY", "UP", "DOWN").contains(direction)) direction = "ANY";
    String category = value(node, "category", "OTHER").toUpperCase(Locale.ROOT);
    if (!List.of("SUPPORT", "RESISTANCE", "ENTRY", "ADD", "TAKE_PROFIT",
        "STOP_LOSS", "TARGET", "CURRENT", "HISTORICAL", "OTHER").contains(category)) {
      category = "OTHER";
    }
    String source = "OCR".equalsIgnoreCase(value(node, "source", "TEXT")) ? "OCR" : "TEXT";
    Creation creation = creation(symbol);
    return new Candidate(
        "candidate-" + index, value(node, "instrumentName", symbol), symbol,
        value(node, "market", "UNKNOWN").toUpperCase(Locale.ROOT), type,
        lower, upper, target, direction, category, value(node, "note", ""),
        value(node, "sourceQuote", ""), source, creation.status(), creation.message());
  }

  private Creation creation(String symbol) {
    Instrument instrument = instruments.findBySymbol(symbol).orElse(null);
    if (instrument == null) return new Creation("VALIDATE_ON_CREATE", "创建时将自动加入标的并校验行情");
    if (present(instrument.bitgetCategory()) && present(instrument.bitgetSymbol())) {
      return new Creation("READY", "可创建提醒");
    }
    return new Creation("VALIDATE_ON_CREATE", "创建时需要校验 Bitget 行情");
  }

  private String normalizeSymbol(String raw, String name) {
    String original = clean(raw);
    if (SYMBOL_ALIASES.containsKey(original)) return SYMBOL_ALIASES.get(original);
    String value = original.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    if (SYMBOL_ALIASES.containsKey(value)) return SYMBOL_ALIASES.get(value);
    if (value.isBlank()) return SYMBOL_ALIASES.getOrDefault(clean(name), "");
    return value;
  }

  private BigDecimal decimal(JsonNode node) {
    if (node == null || node.isNull() || node.asText("").isBlank()) return null;
    try {
      return new BigDecimal(node.asText()).stripTrailingZeros();
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String candidateKey(Candidate item) {
    return String.join("|", item.symbol(), item.alertType(), String.valueOf(item.lowerPrice()),
        String.valueOf(item.upperPrice()), String.valueOf(item.targetPrice()), item.triggerDirection());
  }

  private String value(JsonNode node, String field, String fallback) {
    String value = node.path(field).asText("").trim();
    return value.isBlank() ? fallback : value;
  }

  private String stripFence(String value) {
    String text = clean(value);
    return text.startsWith("```")
        ? text.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim() : text;
  }

  private boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }

  private boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private boolean hasSemanticContent(String value) {
    String compact = IMAGE.matcher(clean(value)).replaceAll("")
        .replaceAll("(?i)https?://\\S+", "")
        .replaceAll("\\[/?图片(?:转文字)?[^]]*]", "")
        .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]", "");
    return compact.length() >= 2 && !List.of("图片", "一张图片", "查看图片").contains(compact);
  }

  private boolean imagePlaceholder(String value) {
    String compact = clean(value).replaceAll("(?i)https?://\\S+", "")
        .replaceAll("(?m)^WXPUSHER_IMAGE_URL=.*$", "")
        .replaceAll("\\s+", " ").trim();
    if (compact.length() > 120 || !compact.matches(".*图片[。.]?$")) return false;
    return !compact.matches(".*(?:\\d|支撑|压力|入场|加仓|止盈|止损|目标|现价|当前价|突破|跌破|附近|挂单).*?");
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String message(Throwable error) {
    return error.getMessage() == null || error.getMessage().isBlank()
        ? error.getClass().getSimpleName() : error.getMessage();
  }

  private record PreparedContent(String text, String ocrText, boolean hasImages) { }
  private record Creation(String status, String message) { }
}
