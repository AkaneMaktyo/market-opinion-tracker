package com.personal.tracker.service.market;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import java.util.List;

public interface MarketBarProvider {
  String name();

  List<MarketBar> fetch(Instrument instrument, String timeframe);

  List<MarketBar> fetch(
      Instrument instrument,
      String timeframe,
      Long startTime,
      Long endTime,
      int limit);
}
