package com.personal.tracker.web;

import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.repository.SessionRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
  private final SessionRepository sessions;

  public SessionController(SessionRepository sessions) {
    this.sessions = sessions;
  }

  @GetMapping
  List<LiveSession> list(
      @RequestParam(required = false) String kolId,
      @RequestParam(defaultValue = "20") int limit) {
    return sessions.findRecent(kolId, limit);
  }

  @GetMapping("/{id}")
  LiveSession get(@PathVariable String id) {
    return sessions.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("直播记录不存在"));
  }

  @PostMapping
  LiveSession create(@RequestBody CreateSessionRequest request) {
    return sessions.create(
        request.kolId(),
        request.sessionDate(),
        request.title(),
        request.source(),
        request.rawText());
  }

  public record CreateSessionRequest(
      String kolId,
      String sessionDate,
      String title,
      String source,
      String rawText) {
  }
}
