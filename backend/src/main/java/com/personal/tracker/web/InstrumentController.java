package com.personal.tracker.web;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.repository.MarketBarRepository.DailySnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {
  private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
  private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);

  private final InstrumentRepository instruments;
  private final MarketBarRepository marketBars;

  public InstrumentController(
      InstrumentRepository instruments,
      MarketBarRepository marketBars) {
    this.instruments = instruments;
    this.marketBars = marketBars;
  }

  @GetMapping
  List<InstrumentView> list(
      @RequestParam(required = false) String kolId,
      @RequestParam(required = false) String query) {
    List<Instrument> items = kolId == null || kolId.isBlank()
        ? instruments.findAll(query)
        : instruments.findByKol(kolId, query);
    Map<String, DailySnapshot> snapshots = marketBars.latestDailySnapshots(
        items.stream().map(Instrument::id).toList(),
        currentMarketDate().toString());
    return items.stream()
        .map(item -> viewOf(item, snapshots.get(item.id())))
        .toList();
  }

  @PostMapping
  Instrument create(@RequestBody CreateInstrumentRequest request) {
    return instruments.saveIfAbsent(
        request.symbol(),
        request.name(),
        request.market(),
        request.sector());
  }

  @PutMapping("/{id}")
  InstrumentView rename(
      @PathVariable String id,
      @RequestBody RenameInstrumentRequest request) {
    Optional<Instrument> result = instruments.rename(
        id,
        request.symbol(),
        request.name(),
        request.logoUrl());
    return result.map(item -> viewOf(item, null))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "\u54c1\u79cd\u4e0d\u5b58\u5728"));
  }

  @PostMapping("/{id}/merge")
  Map<String, String> merge(
      @PathVariable String id,
      @RequestBody MergeInstrumentRequest request) {
    instruments.merge(id, request.targetId());
    return Map.of("status", "ok", "message", "\u5f52\u5e76\u5b8c\u6210");
  }

  @PutMapping("/{id}/group")
  InstrumentView updateGroup(
      @PathVariable String id,
      @RequestBody UpdateGroupRequest request) {
    instruments.updateGroup(id, request.groupName());
    return instruments.findById(id)
        .map(item -> viewOf(item, null))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "\u54c1\u79cd\u4e0d\u5b58\u5728"));
  }

  @GetMapping("/groups")
  List<String> groups() {
    return instruments.findAllGroups();
  }

  private static InstrumentView viewOf(Instrument item, DailySnapshot snapshot) {
    BigDecimal changePct = changePct(snapshot);
    return new InstrumentView(
        item.id(),
        item.symbol(),
        item.name(),
        item.market(),
        item.sector(),
        item.groupName(),
        item.logoUrl(),
        item.createdAt(),
        changePct == null ? null : snapshot.close(),
        changePct,
        changePct == null ? null : snapshot.barTime());
  }

  private static BigDecimal changePct(DailySnapshot snapshot) {
    if (snapshot == null || snapshot.close() == null || snapshot.previousClose() == null) {
      return null;
    }
    if (BigDecimal.ZERO.compareTo(snapshot.previousClose()) == 0) {
      return null;
    }
    return snapshot.close()
        .subtract(snapshot.previousClose())
        .multiply(BigDecimal.valueOf(100))
        .divide(snapshot.previousClose(), 2, RoundingMode.HALF_UP);
  }

  private static LocalDate currentMarketDate() {
    ZonedDateTime now = ZonedDateTime.now(MARKET_ZONE);
    LocalDate date = now.toLocalDate();
    if (isWeekend(date) || now.toLocalTime().isBefore(MARKET_OPEN)) {
      return previousWeekday(date.minusDays(1));
    }
    return date;
  }

  private static LocalDate previousWeekday(LocalDate date) {
    LocalDate current = date;
    while (isWeekend(current)) {
      current = current.minusDays(1);
    }
    return current;
  }

  private static boolean isWeekend(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
  }

  public record CreateInstrumentRequest(
      String symbol,
      String name,
      String market,
      String sector) {
  }

  public record RenameInstrumentRequest(
      String symbol,
      String name,
      String logoUrl) {
  }

  public record MergeInstrumentRequest(
      String targetId) {
  }

  public record UpdateGroupRequest(
      String groupName) {
  }

  public record InstrumentView(
      String id,
      String symbol,
      String name,
      String market,
      String sector,
      String groupName,
      String logoUrl,
      String createdAt,
      BigDecimal dayClose,
      BigDecimal dayChangePct,
      String dayBarTime) {
  }
}
