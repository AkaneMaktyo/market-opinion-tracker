package com.personal.tracker.web;

import com.personal.tracker.service.ImportService;
import com.personal.tracker.service.ImportService.ImportCommitRequest;
import com.personal.tracker.service.ImportService.ImportCommitResult;
import com.personal.tracker.service.ImportService.ImportPreview;
import com.personal.tracker.service.ImportService.ImportPreviewRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/imports")
public class ImportController {
  private final ImportService imports;

  public ImportController(ImportService imports) {
    this.imports = imports;
  }

  @PostMapping("/preview")
  ImportPreview preview(@RequestBody ImportPreviewRequest request) {
    return imports.preview(request);
  }

  @PostMapping("/commit")
  ImportCommitResult commit(@RequestBody ImportCommitRequest request) {
    return imports.commit(request);
  }
}
