package com.ianctchinese.controller;

import com.ianctchinese.dto.UserCreateRequest;
import com.ianctchinese.dto.UserStatusUpdateRequest;
import com.ianctchinese.dto.UserSummaryResponse;
import com.ianctchinese.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping
  public ResponseEntity<List<UserSummaryResponse>> listUsers() {
    return ResponseEntity.ok(userService.listUsers());
  }

  @PostMapping
  public ResponseEntity<UserSummaryResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
    return ResponseEntity.ok(userService.createUser(request));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<UserSummaryResponse> updateStatus(
      @PathVariable Long id,
      @Valid @RequestBody UserStatusUpdateRequest request) {
    return ResponseEntity.ok(userService.updateStatus(id, request));
  }
}
