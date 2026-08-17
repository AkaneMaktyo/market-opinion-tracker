package com.personal.tracker.web.positions;

import com.personal.tracker.domain.KolPosition;
import com.personal.tracker.domain.KolPositionTrade;
import com.personal.tracker.service.positions.KolPositionRebuildService;
import com.personal.tracker.service.positions.KolPositionService;
import com.personal.tracker.service.positions.KolPositionService.OpenPositionCommand;
import com.personal.tracker.service.positions.PositionStatsView;
import com.personal.tracker.service.positions.PositionView;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/positions")
public class PositionController {
  private final KolPositionService positions;
  private final KolPositionRebuildService rebuildService;

  public PositionController(
      KolPositionService positions,
      KolPositionRebuildService rebuildService) {
    this.positions = positions;
    this.rebuildService = rebuildService;
  }

  @GetMapping
  List<PositionView> list(
      @RequestParam(required = false) String kolId,
      @RequestParam(defaultValue = "false") boolean includeClosed) {
    return positions.list(kolId, includeClosed);
  }

  @GetMapping("/stats")
  PositionStatsView stats(@RequestParam(required = false) String kolId) {
    return positions.stats(kolId);
  }

  @GetMapping("/trades")
  List<KolPositionTrade> trades(
      @RequestParam(required = false) String kolId,
      @RequestParam(defaultValue = "200") int limit) {
    return positions.trades(kolId, limit);
  }

  @PostMapping("/rebuild")
  KolPositionRebuildService.RebuildResult rebuild(
      @RequestParam(required = false) String kolId,
      @RequestParam(required = false) String sourceInclude) {
    return rebuildService.rebuild(kolId, sourceInclude);
  }

  @PostMapping
  KolPosition open(@RequestBody OpenPositionRequest request) {
    return positions.openManual(new OpenPositionCommand(
        request.kolId(),
        request.symbol(),
        request.name(),
        request.market(),
        request.sector(),
        request.direction(),
        request.entryPrice()));
  }

  @PostMapping("/{id}/close")
  KolPosition close(@PathVariable String id) {
    return positions.closeManual(id);
  }

  public record OpenPositionRequest(
      String kolId,
      String symbol,
      String name,
      String market,
      String sector,
      String direction,
      BigDecimal entryPrice) {
  }
}
