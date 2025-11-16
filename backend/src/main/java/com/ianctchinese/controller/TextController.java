package com.ianctchinese.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ianctchinese.dto.TextCategoryUpdateRequest;
import com.ianctchinese.dto.TextUploadRequest;
import com.ianctchinese.model.TextDocument;
import com.ianctchinese.service.ExportService;
import com.ianctchinese.service.TextService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/texts")
@RequiredArgsConstructor
public class TextController {

  private final TextService textService;
  private final ExportService exportService;
  private final ObjectMapper objectMapper;

  @PostMapping
  public ResponseEntity<TextDocument> uploadText(@Valid @RequestBody TextUploadRequest request) {
    return ResponseEntity.ok(textService.createText(request));
  }

  @GetMapping
  public ResponseEntity<List<TextDocument>> listTexts(
      @RequestParam(name = "category", required = false) String category) {
    return ResponseEntity.ok(textService.listTexts(category));
  }

  @GetMapping("/search")
  public ResponseEntity<List<TextDocument>> search(@RequestParam("keyword") String keyword) {
    return ResponseEntity.ok(textService.searchTexts(keyword));
  }

  @GetMapping("/{id}")
  public ResponseEntity<TextDocument> getText(@PathVariable Long id) {
    return ResponseEntity.ok(textService.getText(id));
  }

  @PatchMapping("/{id}/category")
  public ResponseEntity<TextDocument> updateCategory(@PathVariable Long id,
      @Valid @RequestBody TextCategoryUpdateRequest request) {
    return ResponseEntity.ok(textService.updateCategory(id, request.getCategory()));
  }

  @GetMapping("/{id}/export")
  public ResponseEntity<byte[]> exportText(@PathVariable Long id) throws JsonProcessingException {
    var bundle = exportService.buildExport(id);
    byte[] payload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=text-" + id + ".json")
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteText(@PathVariable Long id) {
    textService.deleteText(id);
    return ResponseEntity.noContent().build();
  }
}
