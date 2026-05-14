package com.personal.tracker.web;

import com.personal.tracker.domain.Kol;
import com.personal.tracker.repository.KolRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kols")
public class KolController {
  private final KolRepository kols;

  public KolController(KolRepository kols) {
    this.kols = kols;
  }

  @GetMapping
  List<Kol> list() {
    return kols.findAll();
  }

  @PostMapping
  Kol create(@RequestBody CreateKolRequest request) {
    return kols.save(request.name(), request.description());
  }

  public record CreateKolRequest(String name, String description) {
  }
}
