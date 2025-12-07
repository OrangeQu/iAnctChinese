package com.ianctchinese.controller;

import com.ianctchinese.llm.GeoService;
import com.ianctchinese.llm.dto.GeoLocateRequest;
import com.ianctchinese.llm.dto.GeoPointDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {

  private final GeoService geoService;

  @PostMapping("/locate")
  public ResponseEntity<List<GeoPointDto>> locate(@RequestBody GeoLocateRequest request) {
    return ResponseEntity.ok(geoService.locate(request));
  }
}
