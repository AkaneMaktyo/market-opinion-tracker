package com.personal.tracker.web.positions;

import com.personal.tracker.domain.KolPosition;
import com.personal.tracker.service.positions.KolPositionService;
import com.personal.tracker.service.positions.KolPositionService.OpenPositionCommand;
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

  public PositionController(KolPositionService positions) {
    this.positions = positions;
  }

  @GetMapping
  List<KolPosition> list(
      @RequestParam(required = false) String kolId,
      @RequestParam(defaultValue = "false") boolean includeClosed) {
    return positions.list(kolId, includeClosed);
  }

  @PostMapping
  KolPosition open(@RequestBody OpenPositionRequest request) {
    return positions.openManual(new OpenPositionCommand(
        request.kolId(),
        request.symbol(),
        request.name(),
        request.market(),
        request.sector()));
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
      String sector) {
  }
}
