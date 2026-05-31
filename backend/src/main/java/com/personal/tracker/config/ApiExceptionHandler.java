package com.personal.tracker.config;

import com.personal.tracker.service.trading.BitgetDemoClient.BitgetClientException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, String> badRequest(IllegalArgumentException error) {
    return Map.of("message", error.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map<String, String> badState(IllegalStateException error) {
    return Map.of("message", error.getMessage());
  }

  @ExceptionHandler(BitgetClientException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  Map<String, String> bitgetError(BitgetClientException error) {
    return Map.of("message", error.getMessage());
  }
}
