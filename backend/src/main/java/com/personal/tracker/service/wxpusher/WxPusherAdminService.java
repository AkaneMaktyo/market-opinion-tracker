package com.personal.tracker.service.wxpusher;

import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.SaveCommand;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.UpdateCommand;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WxPusherAdminService {
  private static final List<DefaultBlogger> DEFAULT_BLOGGERS = List.of(
      new DefaultBlogger("华尔街阿宝分享", List.of("华尔街阿宝", "阿宝")),
      new DefaultBlogger("猫姐会员频道", List.of("猫姐")),
      new DefaultBlogger("幂笈投资", List.of("幂笈")),
      new DefaultBlogger("牛顿师兄", List.of("牛顿")),
      new DefaultBlogger("美股投资网", List.of("美股投资网")));
  private final KolRepository kols;
  private final WxPusherSettingsRepository settingsRepository;
  private final WxPusherBloggerRepository bloggerRepository;
  private final WxPusherMessageRepository messageRepository;
  private final OpenAiJsonExtractor aiExtractor;
  private final WxPusherIngestionService ingestion;
  private final WxPusherMonitorLifecycle lifecycle;

  public WxPusherAdminService(
      KolRepository kols,
      WxPusherSettingsRepository settingsRepository,
      WxPusherBloggerRepository bloggerRepository,
      WxPusherMessageRepository messageRepository,
      OpenAiJsonExtractor aiExtractor,
      WxPusherIngestionService ingestion,
      WxPusherMonitorLifecycle lifecycle) {
    this.kols = kols;
    this.settingsRepository = settingsRepository;
    this.bloggerRepository = bloggerRepository;
    this.messageRepository = messageRepository;
    this.aiExtractor = aiExtractor;
    this.ingestion = ingestion;
    this.lifecycle = lifecycle;
  }

  public WxPusherSettings settings() {
    return settingsRepository.get();
  }

  public WxPusherSettings updateSettings(UpdateCommand command) {
    WxPusherSettings settings = settingsRepository.update(command);
    lifecycle.refresh();
    return settings;
  }

  public StatusView status() {
    List<WxPusherBlogger> bloggers = ensureDefaultBloggers();
    var runtime = lifecycle.runtimeState();
    var settings = settingsRepository.get();
    var llm = aiExtractor.health();
    String issue = settings.configurationIssue();
    String runtimeError = runtime.lastError() == null || runtime.lastError().isBlank()
        ? settings.lastError()
        : runtime.lastError();
    return new StatusView(
        runtime.running() && issue.isBlank() && (settings.enablePolling() || settings.enableWebsocket()),
        websocketState(settings, runtime),
        settings.lastPollAt(),
        settings.lastHeartbeatAt(),
        issue.isBlank() ? runtimeError : issue,
        settings.enablePolling(),
        settings.enableWebsocket(),
        bloggers.size(),
        bloggers.stream().filter(WxPusherBlogger::enabled).map(WxPusherBlogger::id).distinct().toList().size(),
        llm.configured(),
        llm.reachable(),
        llm.message(),
        llm.checkedAt());
  }

  public List<WxPusherBlogger> bloggers() {
    return ensureDefaultBloggers();
  }

  public WxPusherBlogger createBlogger(BloggerCommand command) {
    var kol = kols.save(command.bloggerName(), "WxPusher 博主");
    WxPusherBlogger created = bloggerRepository.create(new SaveCommand(
        null,
        kol.id(),
        command.bloggerName(),
        command.aliases(),
        command.enabled(),
        "LAST_30",
        command.enabled() ? null : ""));
    lifecycle.refresh();
    return created;
  }

  public WxPusherBlogger updateBlogger(BloggerCommand command) {
    WxPusherBlogger current = bloggerRepository.findById(command.id());
    var kol = kols.save(command.bloggerName(), "WxPusher 博主");
    String seedCompletedAt = shouldResetSeed(current, command) ? null : current.seedCompletedAt();
    WxPusherBlogger updated = bloggerRepository.update(new SaveCommand(
        command.id(),
        kol.id(),
        command.bloggerName(),
        command.aliases(),
        command.enabled(),
        current.historySeedMode(),
        seedCompletedAt));
    lifecycle.refresh();
    return updated;
  }

  public List<WxPusherMessage> messages(String status, String kolId, int limit) {
    return messageRepository.list(status, kolId, limit);
  }

  public WxPusherMessage retry(String id) {
    ingestion.retry(id);
    return messageRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
  }

  private boolean shouldResetSeed(WxPusherBlogger current, BloggerCommand command) {
    return command.enabled()
        && (!current.enabled()
        || !current.bloggerName().equals(command.bloggerName())
        || !current.aliases().equals(command.aliases()));
  }

  private List<WxPusherBlogger> ensureDefaultBloggers() {
    List<WxPusherBlogger> current = bloggerRepository.list();
    Set<String> names = current.stream()
        .map(WxPusherBlogger::bloggerName)
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    boolean changed = false;
    for (DefaultBlogger blogger : DEFAULT_BLOGGERS) {
      if (names.contains(blogger.name())) {
        continue;
      }
      createDefaultBlogger(blogger);
      names.add(blogger.name());
      changed = true;
    }
    if (changed) {
      lifecycle.refresh();
      return bloggerRepository.list();
    }
    return current;
  }

  private void createDefaultBlogger(DefaultBlogger blogger) {
    var kol = kols.save(blogger.name(), "WxPusher 博主");
    bloggerRepository.create(new SaveCommand(
        null,
        kol.id(),
        blogger.name(),
        blogger.aliases(),
        true,
        "LAST_30",
        null));
  }

  private String websocketState(
      WxPusherSettings settings,
      WxPusherMonitorLifecycle.RuntimeState runtime) {
    if (!settings.enableWebsocket()) {
      return settings.enablePolling() ? "POLLING_ONLY" : "IDLE";
    }
    if (!settings.pushToken().isBlank() && !settings.deviceToken().isBlank()) {
      return runtime.websocketState();
    }
    if (!settings.websocketReady() && !settings.pollingReady()) {
      return "ERROR";
    }
    return runtime.websocketState();
  }

  public record BloggerCommand(
      String id,
      String bloggerName,
      List<String> aliases,
      boolean enabled) {
  }

  public record StatusView(
      boolean running,
      String websocketState,
      String lastPollAt,
      String lastHeartbeatAt,
      String lastError,
      boolean pollingEnabled,
      boolean websocketEnabled,
      int totalBloggers,
      int enabledBloggers,
      boolean llmConfigured,
      boolean llmReachable,
      String llmMessage,
      String llmCheckedAt) {
  }

  private record DefaultBlogger(String name, List<String> aliases) {
  }
}
