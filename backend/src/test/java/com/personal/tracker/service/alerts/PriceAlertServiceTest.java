package com.personal.tracker.service.alerts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.alerts.PriceAlertRepository;
import com.personal.tracker.repository.alerts.PriceAlertRepository.PriceAlertView;
import com.personal.tracker.service.alerts.PriceAlertService.BatchItem;
import com.personal.tracker.service.alerts.recognition.MessagePriceAlertRecognitionService;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Candidate;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Result;
import com.personal.tracker.service.market.BitgetMarketBarProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PriceAlertServiceTest {
  private PriceAlertRepository alerts;
  private InstrumentRepository instruments;
  private PriceAlertMonitor monitor;
  private MessagePriceAlertRecognitionService recognitions;
  private PriceAlertService service;

  @BeforeEach
  void setUp() {
    alerts = mock(PriceAlertRepository.class);
    instruments = mock(InstrumentRepository.class);
    monitor = mock(PriceAlertMonitor.class);
    recognitions = mock(MessagePriceAlertRecognitionService.class);
    service = new PriceAlertService(
        alerts, instruments, mock(BitgetMarketBarProvider.class), monitor, recognitions);
  }

  @Test
  void refreshesCurrentPricesWhenListingAlerts() {
    when(alerts.list()).thenReturn(List.of());

    assertEquals(List.of(), service.list());

    verify(monitor).refreshCurrentPrices();
  }

  @Test
  void linksRecognitionSourceWhenEquivalentAlertAlreadyExists() {
    Candidate candidate = new Candidate(
        "candidate-1", "谷歌", "GOOGL", "US", "POINT", price("335"), price("335"),
        price("335"), "ANY", "SUPPORT", "", "335支撑", "TEXT", "", "");
    Result recognition = new Result(
        "recognition-1", "message-1", "SUCCESS", List.of(candidate), List.of(), "", "now");
    Instrument instrument = new Instrument(
        "instrument-1", "GOOGL", "谷歌", "US", null, null, null, null,
        "SPOT", "GOOGLUSDT", "MAPPED", "now", "now");
    PriceAlertView existing = alert(null);
    PriceAlertView linked = alert("message-1");
    when(recognitions.require("recognition-1")).thenReturn(recognition);
    when(alerts.findBySource("recognition-1", "candidate-1")).thenReturn(Optional.empty());
    when(instruments.findBySymbol("GOOGL")).thenReturn(Optional.of(instrument));
    when(alerts.findEquivalent(
        "instrument-1", "POINT", "ANY", price("335"), price("335"), price("335")))
        .thenReturn(Optional.of(existing));
    when(alerts.linkSourceIfMissing("alert-1", "recognition-1", "candidate-1"))
        .thenReturn(linked);

    var result = service.createBatch("recognition-1", "default", List.of(new BatchItem(
        "candidate-1", "谷歌", "GOOGL", "US", "POINT", "ANY",
        price("335"), price("335"), price("335"))));

    assertEquals("EXISTS", result.items().get(0).status());
    assertEquals("message-1", result.items().get(0).alert().sourceMessageId());
    verify(alerts).linkSourceIfMissing("alert-1", "recognition-1", "candidate-1");
  }

  private PriceAlertView alert(String sourceMessageId) {
    return new PriceAlertView(
        "alert-1", "instrument-1", "GOOGL", "谷歌", "POINT", "ANY",
        price("335"), price("335"), price("335"), "ACTIVE", price("330"), "now",
        null, "WAITING", null, sourceMessageId == null ? null : "recognition-1",
        sourceMessageId == null ? null : "candidate-1", sourceMessageId, "now", "now");
  }

  private BigDecimal price(String value) {
    return new BigDecimal(value);
  }
}
