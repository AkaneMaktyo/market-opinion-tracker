package com.personal.tracker.web;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.repository.InstrumentRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {
  private final InstrumentRepository instruments;

  public InstrumentController(InstrumentRepository instruments) {
    this.instruments = instruments;
  }

  @GetMapping
  List<Instrument> list(
      @RequestParam(required = false) String kolId,
      @RequestParam(required = false) String query) {
    return kolId == null || kolId.isBlank()
        ? instruments.findAll(query)
        : instruments.findByKol(kolId, query);
  }

  @PostMapping
  Instrument create(@RequestBody CreateInstrumentRequest request) {
    return instruments.saveIfAbsent(
        request.symbol(),
        request.name(),
        request.market(),
        request.sector());
  }

  public record CreateInstrumentRequest(
      String symbol,
      String name,
      String market,
      String sector) {
  }
}
