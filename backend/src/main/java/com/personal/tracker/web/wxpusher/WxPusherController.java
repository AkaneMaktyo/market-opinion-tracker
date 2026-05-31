package com.personal.tracker.web.wxpusher;

import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.UpdateCommand;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import com.personal.tracker.service.wxpusher.WxPusherAdminService;
import com.personal.tracker.service.wxpusher.WxPusherAdminService.BloggerCommand;
import com.personal.tracker.service.wxpusher.WxPusherAdminService.StatusView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wxpusher")
public class WxPusherController {
  private final WxPusherAdminService admin;

  public WxPusherController(WxPusherAdminService admin) {
    this.admin = admin;
  }

  @GetMapping("/settings")
  WxPusherSettings settings() {
    return admin.settings();
  }

  @PutMapping("/settings")
  WxPusherSettings updateSettings(@RequestBody SettingsRequest request) {
    return admin.updateSettings(new UpdateCommand(
        request.deviceToken(),
        request.pushToken(),
        request.deviceUuid(),
        request.platform(),
        request.version(),
        request.pollIntervalSeconds(),
        request.enablePolling(),
        request.enableWebsocket()));
  }

  @GetMapping("/status")
  StatusView status() {
    return admin.status();
  }

  @GetMapping("/bloggers")
  List<WxPusherBlogger> bloggers() {
    return admin.bloggers();
  }

  @PostMapping("/bloggers")
  WxPusherBlogger createBlogger(@RequestBody BloggerRequest request) {
    return admin.createBlogger(new BloggerCommand(
        null,
        request.bloggerName(),
        request.aliases(),
        request.enabled()));
  }

  @PutMapping("/bloggers")
  WxPusherBlogger updateBlogger(@RequestBody BloggerRequest request) {
    return admin.updateBlogger(new BloggerCommand(
        request.id(),
        request.bloggerName(),
        request.aliases(),
        request.enabled()));
  }

  @GetMapping("/messages")
  List<WxPusherMessage> messages(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String kolId,
      @RequestParam(defaultValue = "30") int limit) {
    return admin.messages(status, kolId, limit);
  }

  @PostMapping("/messages/{id}/retry")
  WxPusherMessage retry(@PathVariable String id) {
    return admin.retry(id);
  }

  public record SettingsRequest(
      String deviceToken,
      String pushToken,
      String deviceUuid,
      String platform,
      String version,
      int pollIntervalSeconds,
      boolean enablePolling,
      boolean enableWebsocket) {
  }

  public record BloggerRequest(
      String id,
      String bloggerName,
      List<String> aliases,
      boolean enabled) {
  }
}
