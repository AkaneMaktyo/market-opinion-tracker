package com.personal.tracker.service.resonance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.repository.resonance.ResonanceRepository;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterDraft;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterItem;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterRecord;
import com.personal.tracker.repository.resonance.ResonanceRepository.ItemDraft;
import com.personal.tracker.repository.resonance.ResonanceRepository.OpinionSignal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResonanceServiceTest {
  @Test
  void listDoesNotCreateRadarItemsFromRecentKeywordMessage() {
    var repository = mock(ResonanceRepository.class);
    var service = new ResonanceService(repository, mock(ResonanceNotifier.class));
    when(repository.list(eq(""), any(), any(Integer.class))).thenReturn(List.of());

    var result = service.list("", 8);

    assertEquals(0, result.size());
  }

  @Test
  void listReturnsStoredClustersFromRecentWindowOnly() {
    var repository = mock(ResonanceRepository.class);
    var service = new ResonanceService(repository, mock(ResonanceNotifier.class));
    ClusterRecord nvda = cluster("NVDA", 95, "2026-07-06T09:30:00Z");
    when(repository.list(eq(""), any(), any(Integer.class))).thenReturn(List.of(nvda));
    when(repository.items(nvda.id())).thenReturn(List.of(item("o1", "SUPPORT")));

    var result = service.list("", 8);

    assertEquals(1, result.size());
    assertEquals("NVDA", result.get(0).cluster().symbol());
    assertEquals("短线", result.get(0).cluster().horizon());
  }

  @Test
  void createsStrongClusterForThreeIndependentSources() {
    var repository = mock(ResonanceRepository.class);
    var notifier = mock(ResonanceNotifier.class);
    var service = new ResonanceService(repository, notifier);
    var saved = new AtomicReference<ClusterDraft>();
    when(repository.recentSignals("NVDA", 200)).thenReturn(List.of(
        signal("o1", "Alpha", "BULLISH", "NVDA"),
        signal("o2", "Beta", "BULLISH", "NVDA"),
        signal("o3", "Gamma", "BULLISH", "NVDA")));
    when(repository.save(any())).thenAnswer(call -> {
      ClusterDraft draft = call.getArgument(0);
      saved.set(draft);
      return cluster(draft);
    });
    when(repository.cluster(any())).thenAnswer(call -> cluster(saved.get()));
    when(repository.items(any())).thenReturn(List.of(item("o1", "SUPPORT")));

    var result = service.refreshForSymbol("NVDA");

    ArgumentCaptor<ClusterDraft> draft = ArgumentCaptor.forClass(ClusterDraft.class);
    verify(repository).save(draft.capture());
    assertEquals(1, result.size());
    assertEquals("STRONG", draft.getValue().grade());
    assertTrue(draft.getValue().score() >= 85);
    assertEquals(3, draft.getValue().sourceCount());
    verify(notifier).notifyIfNeeded(any(), any());
  }

  @Test
  void keepsConflictItemsAndLowersGrade() {
    var repository = mock(ResonanceRepository.class);
    var notifier = mock(ResonanceNotifier.class);
    var service = new ResonanceService(repository, notifier);
    var saved = new AtomicReference<ClusterDraft>();
    when(repository.recentSignals("TSLA", 200)).thenReturn(List.of(
        signal("o1", "Alpha", "BULLISH", "TSLA"),
        signal("o2", "Beta", "BULLISH", "TSLA"),
        signal("o3", "Gamma", "BULLISH", "TSLA"),
        signal("o4", "Delta", "BEARISH", "TSLA")));
    when(repository.save(any())).thenAnswer(call -> {
      ClusterDraft draft = call.getArgument(0);
      saved.set(draft);
      return cluster(draft);
    });
    when(repository.cluster(any())).thenAnswer(call -> cluster(saved.get()));
    when(repository.items(any())).thenReturn(List.of(item("o1", "SUPPORT"), item("o4", "CONFLICT")));

    service.refreshForSymbol("TSLA");

    ArgumentCaptor<ClusterDraft> draft = ArgumentCaptor.forClass(ClusterDraft.class);
    ArgumentCaptor<List<ItemDraft>> items = ArgumentCaptor.forClass(List.class);
    verify(repository).save(draft.capture());
    verify(repository).replaceItems(any(), items.capture());
    assertEquals("ACTIONABLE", draft.getValue().grade());
    assertEquals(1, draft.getValue().conflictCount());
    assertTrue(items.getValue().stream().anyMatch(item -> "CONFLICT".equals(item.role())));
  }

  private OpinionSignal signal(String id, String source, String direction, String symbol) {
    return new OpinionSignal(
        id,
        "instrument-1",
        symbol,
        source,
        direction,
        "短线",
        source + " thesis",
        "突破前高",
        "跌破支撑",
        80,
        "原文",
        "波动风险",
        "AI 订单",
        "关键位",
        "2026-06-05T10:00:00Z");
  }

  private ClusterRecord cluster(ClusterDraft draft) {
    return new ClusterRecord(
        "cluster-1",
        draft.instrumentId(),
        draft.symbol(),
        draft.bucketDate(),
        draft.direction(),
        draft.horizon(),
        draft.score(),
        draft.grade(),
        draft.action(),
        draft.summary(),
        draft.triggerText(),
        draft.invalidationText(),
        draft.riskText(),
        draft.catalystText(),
        draft.sourceCount(),
        draft.opinionCount(),
        draft.supportCount(),
        draft.conflictCount(),
        draft.sourceNames(),
        draft.lastOpinionAt(),
        "ACTIVE",
        "PENDING",
        "",
        "",
        "now",
        "now");
  }

  private ClusterItem item(String opinionId, String role) {
    return new ClusterItem(opinionId, role, "Alpha", "BULLISH", "短线", "thesis", "quote", "now");
  }

  private ClusterRecord cluster(String symbol, int score, String lastOpinionAt) {
    return new ClusterRecord(
        "cluster-" + symbol,
        "instrument-" + symbol,
        symbol,
        "2026-07-06",
        "BULLISH",
        "短线",
        score,
        "STRONG",
        "关注机会",
        symbol + " summary",
        "",
        "",
        "",
        "",
        2,
        2,
        2,
        0,
        "Alpha, Beta",
        lastOpinionAt,
        "ACTIVE",
        "PENDING",
        "",
        "",
        "now",
        "now");
  }

}
