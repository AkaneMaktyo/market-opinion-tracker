package com.personal.tracker.service.json;

import com.personal.tracker.service.ImportService.ImportPreview;

public interface JsonOpinionParser {
  ImportPreview parse(String rawJson) throws Exception;
}
