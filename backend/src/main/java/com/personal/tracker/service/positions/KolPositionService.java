package com.personal.tracker.service.positions;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.KolPosition;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.positions.KolPositionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KolPositionService {
  private final KolRepository kols;
  private final InstrumentRepository instruments;
  private final KolPositionRepository positions;
  private final PositionActionResolver actionResolver;

  public KolPositionService(
      KolRepository kols,
      InstrumentRepository instruments,
      KolPositionRepository positions,
      PositionActionResolver actionResolver) {
    this.kols = kols;
    this.instruments = instruments;
    this.positions = positions;
    this.actionResolver = actionResolver;
  }

  public List<KolPosition> list(String kolId, boolean includeClosed) {
    return positions.list(kols.normalize(kolId), includeClosed);
  }

  public KolPosition openManual(OpenPositionCommand command) {
    String kolId = kols.normalize(command.kolId());
    if (command.symbol() == null || command.symbol().isBlank()) {
      throw new IllegalArgumentException("请先填写持仓代码");
    }
    Instrument instrument = instruments.saveIfAbsent(
        command.symbol(),
        command.name(),
        command.market(),
        command.sector());
    return positions.open(kolId, instrument.id(), "", "MANUAL_OPEN");
  }

  public KolPosition closeManual(String id) {
    return positions.closeById(id)
        .orElseThrow(() -> new IllegalArgumentException("持仓不存在"));
  }

  public void apply(String kolId, Instrument instrument, String opinionId, String action) {
    String resolved = actionResolver.normalize(action);
    if (PositionActionResolver.OPEN.equals(resolved)) {
      positions.open(kols.normalize(kolId), instrument.id(), opinionId, resolved);
    }
    if (PositionActionResolver.CLOSE.equals(resolved)) {
      positions.close(kols.normalize(kolId), instrument.id(), opinionId, resolved);
    }
  }

  public record OpenPositionCommand(
      String kolId,
      String symbol,
      String name,
      String market,
      String sector) {
  }
}
