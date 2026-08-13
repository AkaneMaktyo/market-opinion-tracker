package com.personal.tracker.service.alerts.recognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.WxPusherOcrProperties;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Candidate;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Result;
import com.personal.tracker.service.wxpusher.feed.WxPusherFeedService;
import com.personal.tracker.service.wxpusher.feed.WxPusherFeedService.FeedMessage;
import com.personal.tracker.service.wxpusher.ocr.ImageOcrClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MessagePriceAlertRecognitionServiceTest {
  private MessagePriceAlertRecognitionService service;
  private WxPusherFeedService feed;
  private ImageOcrClient ocr;
  private WxPusherOcrProperties properties;
  private PriceAlertRecognitionRepository repository;
  private PriceAlertDeepSeekClient deepSeek;
  private InstrumentRepository instruments;

  @BeforeEach
  void setUp() {
    instruments = mock(InstrumentRepository.class);
    when(instruments.findBySymbol(anyString())).thenReturn(Optional.empty());
    feed = mock(WxPusherFeedService.class);
    ocr = mock(ImageOcrClient.class);
    properties = new WxPusherOcrProperties();
    repository = mock(PriceAlertRecognitionRepository.class);
    deepSeek = mock(PriceAlertDeepSeekClient.class);
    service = new MessagePriceAlertRecognitionService(
        feed, ocr, properties, repository, deepSeek, instruments, new ObjectMapper());
  }

  @Test
  void parsesMultipleSymbolsPointsAndRangesWithoutDroppingCandidates() {
    List<Candidate> result = service.parse("""
        {"candidates":[
          {"instrumentName":"SPCX","symbol":"SPCX","market":"US","alertType":"POINT","targetPrice":144,"triggerDirection":"ANY","category":"SUPPORT","sourceQuote":"144支撑","source":"TEXT"},
          {"instrumentName":"SPCX","symbol":"SPCX","market":"US","alertType":"POINT","targetPrice":148,"triggerDirection":"UP","category":"ENTRY","sourceQuote":"突破148","source":"TEXT"},
          {"instrumentName":"谷歌","symbol":"GOOGL","market":"US","alertType":"RANGE","lowerPrice":350,"upperPrice":335,"category":"RESISTANCE","sourceQuote":"335-350","source":"OCR"},
          {"instrumentName":"IREN","symbol":"IREN","market":"US","alertType":"POINT","targetPrice":63.5,"category":"CURRENT","sourceQuote":"IREN现价63.5","source":"TEXT"},
          {"instrumentName":"CRWV","symbol":"CRWV","market":"US","alertType":"POINT","targetPrice":91,"category":"TARGET","sourceQuote":"CRWV目标91","source":"TEXT"},
          {"instrumentName":"NBIS","symbol":"NBIS","market":"US","alertType":"POINT","targetPrice":72,"category":"HISTORICAL","sourceQuote":"NBIS曾到72","source":"TEXT"}
        ]}
        """);

    assertEquals(6, result.size());
    assertEquals(List.of("SPCX", "SPCX", "GOOGL", "IREN", "CRWV", "NBIS"),
        result.stream().map(Candidate::symbol).toList());
    assertEquals("335", result.get(2).lowerPrice().toPlainString());
    assertEquals("350", result.get(2).upperPrice().toPlainString());
    assertNull(result.get(2).targetPrice());
    assertEquals("OCR", result.get(2).source());
  }

  @Test
  void normalizesChineseAliasAndRemovesExactDuplicatesOrInvalidPrices() {
    List<Candidate> result = service.parse("""
        {"candidates":[
          {"instrumentName":"谷歌","symbol":"谷歌","alertType":"POINT","targetPrice":306,"triggerDirection":"DOWN"},
          {"instrumentName":"谷歌","symbol":"GOOGL","alertType":"POINT","targetPrice":306,"triggerDirection":"DOWN"},
          {"instrumentName":"谷歌","symbol":"GOOGL","alertType":"POINT","targetPrice":0},
          {"instrumentName":"谷歌","symbol":"GOOGL","alertType":"RANGE","lowerPrice":270,"upperPrice":280}
        ]}
        """);

    assertEquals(2, result.size());
    assertEquals("GOOGL", result.get(0).symbol());
    assertEquals("DOWN", result.get(0).triggerDirection());
    assertEquals("RANGE", result.get(1).alertType());
  }

  @Test
  void doesNotCallDeepSeekWhenPureImageHasNoOcrText() {
    properties.setEnabled(false);
    when(repository.find("msg-image")).thenReturn(Optional.empty());
    when(repository.claim("msg-image")).thenReturn(true);
    when(feed.detail("msg-image")).thenReturn(message(
        "msg-image", "图片", "WXPUSHER_IMAGE_URL=http://example.com/only.png"));
    Result failed = new Result(
        "recognition-1", "msg-image", "FAILED", List.of(),
        List.of("图片 1 未识别：OCR 已禁用"), "消息没有可识别的正文或图片文字", "now");
    when(repository.fail(eq("msg-image"), eq(""), anyString(), any())).thenReturn(failed);

    assertEquals("FAILED", service.recognize("msg-image").status());

    verify(deepSeek, never()).recognize(anyString(), anyString(), anyString());
  }

  @Test
  void keepsSuccessfulOcrAndWarningWhenAnotherImageFails() {
    when(repository.find("msg-partial")).thenReturn(Optional.empty());
    when(repository.claim("msg-partial")).thenReturn(true);
    when(feed.detail("msg-partial")).thenReturn(message(
        "msg-partial", "SPCX 144支撑", """
            SPCX 144支撑
            WXPUSHER_IMAGE_URL=http://example.com/one.png
            WXPUSHER_IMAGE_URL=http://example.com/two.png
            """));
    when(ocr.recognize("http://example.com/one.png")).thenReturn("谷歌 335-350 压力");
    when(ocr.recognize("http://example.com/two.png")).thenThrow(new IllegalStateException("图片损坏"));
    when(deepSeek.recognize(eq("msg-partial"), anyString(), anyString()))
        .thenReturn("{\"candidates\":[]}");
    Result empty = new Result(
        "recognition-2", "msg-partial", "EMPTY", List.of(),
        List.of("图片 2 OCR 失败：图片损坏"), "", "now");
    when(repository.complete(eq("msg-partial"), eq("谷歌 335-350 压力"), any(), any()))
        .thenReturn(empty);

    assertEquals("EMPTY", service.recognize("msg-partial").status());

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(deepSeek).recognize(eq("msg-partial"), anyString(), prompt.capture());
    assertEquals(true, prompt.getValue().contains("[图片OCR]\n谷歌 335-350 压力"));
    verify(repository).complete(
        eq("msg-partial"), eq("谷歌 335-350 压力"), any(),
        eq(List.of("图片 2 OCR 失败：图片损坏")));
  }

  @Test
  void addsRecognizedSymbolsToCurrentWatchlist() {
    Candidate candidate = new Candidate(
        "candidate-1", "谷歌", "GOOGL", "US", "POINT", null, null,
        new java.math.BigDecimal("335"), "ANY", "SUPPORT", "", "335支撑", "TEXT", "", "");
    Result cached = new Result(
        "recognition-3", "msg-cached", "SUCCESS", List.of(candidate), List.of(), "", "now");
    Instrument instrument = new Instrument(
        "inst-google", "GOOGL", "谷歌", "US", null, null, null, null,
        null, null, null, null, "now");
    when(repository.find("msg-cached")).thenReturn(Optional.of(cached));
    when(instruments.findBySymbol("GOOGL")).thenReturn(Optional.of(instrument));

    assertEquals("SUCCESS", service.recognize("msg-cached", "kol-1").status());

    verify(instruments).setWatchlist("kol-1", "inst-google", true);
    verify(deepSeek, never()).recognize(anyString(), anyString(), anyString());
  }

  private FeedMessage message(String id, String summary, String detail) {
    return new FeedMessage(
        id, "key", "顺哥", "default", "消息", summary, detail, "",
        "2026-08-13T00:00:00Z", "RECEIVED", "NOT_STARTED", "", 0);
  }
}
