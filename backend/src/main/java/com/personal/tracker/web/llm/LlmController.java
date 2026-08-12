package com.personal.tracker.web.llm;

import com.personal.tracker.repository.llm.LlmCallLogRepository;
import com.personal.tracker.repository.llm.LlmCallLogRepository.LlmCallLog;
import com.personal.tracker.repository.llm.LlmCallLogRepository.SceneSummary;
import com.personal.tracker.repository.llm.LlmCallLogRepository.AuditDetail;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm")
public class LlmController {
  private final LlmCallLogRepository logs;

  public LlmController(LlmCallLogRepository logs) {
    this.logs = logs;
  }

  @GetMapping("/logs")
  List<LlmCallLog> list(
      @RequestParam(required = false) String date,
      @RequestParam(defaultValue = "50") int limit) {
    return logs.list(date, limit);
  }

  @GetMapping("/summary")
  List<SceneSummary> summary(@RequestParam(required = false) String date) {
    return logs.summarize(date);
  }

  @GetMapping("/logs/{id}")
  AuditDetail detail(@PathVariable String id) {
    return logs.detail(id);
  }
}
