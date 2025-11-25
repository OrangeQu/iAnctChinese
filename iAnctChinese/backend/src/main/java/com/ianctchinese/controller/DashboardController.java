package com.ianctchinese.controller;

import com.ianctchinese.repository.EntityAnnotationRepository;
import com.ianctchinese.repository.RelationAnnotationRepository;
import com.ianctchinese.repository.TextDocumentRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final TextDocumentRepository textDocumentRepository;
  private final EntityAnnotationRepository entityAnnotationRepository;
  private final RelationAnnotationRepository relationAnnotationRepository;

  @GetMapping("/overview")
  public ResponseEntity<Map<String, Object>> overview(@RequestParam("textId") Long textId) {
    Map<String, Object> overview = new HashMap<>();
    overview.put("text", textDocumentRepository.findById(textId).orElse(null));
    overview.put("entityCount", entityAnnotationRepository.findByTextDocumentId(textId).size());
    overview.put("relationCount", relationAnnotationRepository.findByTextDocumentId(textId).size());
    overview.put("status", "READY");
    return ResponseEntity.ok(overview);
  }
}
