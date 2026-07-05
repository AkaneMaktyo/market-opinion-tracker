package com.personal.tracker.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.service.MarketDataService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InstrumentControllerTest {
  @Test
  void deletesInstrumentWhenRequested() {
    var instruments = mock(InstrumentRepository.class);
    var marketBars = mock(MarketBarRepository.class);
    var marketData = mock(MarketDataService.class);
    when(instruments.delete("inst-1")).thenReturn(true);
    var controller = new InstrumentController(instruments, marketBars, marketData);

    assertEquals(Map.of("status", "ok", "message", "删除完成"), controller.delete("inst-1"));

    verify(instruments).delete("inst-1");
  }

  @Test
  void returnsNotFoundWhenDeletingMissingInstrument() {
    var instruments = mock(InstrumentRepository.class);
    var marketBars = mock(MarketBarRepository.class);
    var marketData = mock(MarketDataService.class);
    when(instruments.delete("missing")).thenReturn(false);
    var controller = new InstrumentController(instruments, marketBars, marketData);

    ResponseStatusException error = assertThrows(
        ResponseStatusException.class,
        () -> controller.delete("missing"));

    assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
  }
}
