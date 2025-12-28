package com.ianctchinese.controller;

import com.ianctchinese.dto.AdminUserEnabledRequest;
import com.ianctchinese.dto.AdminUserResetPasswordRequest;
import com.ianctchinese.dto.AdminUserResponse;
import com.ianctchinese.service.AdminUserService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final AdminUserService adminUserService;

  @GetMapping
  public ResponseEntity<?> listUsers(
      @RequestParam(value = "query", required = false) String query,
      @RequestParam(value = "enabled", required = false) Boolean enabled,
      Pageable pageable) {
    return ResponseEntity.ok(adminUserService.listUsers(query, enabled, pageable));
  }

  @PutMapping("/{id}/enabled")
  public ResponseEntity<?> updateEnabled(
      @PathVariable Long id,
      @Valid @RequestBody AdminUserEnabledRequest request) {
    try {
      adminUserService.updateEnabled(id, Boolean.TRUE.equals(request.getEnabled()));
      return ResponseEntity.ok().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @PostMapping("/{id}/reset-password")
  public ResponseEntity<?> resetPassword(
      @PathVariable Long id,
      @Valid @RequestBody AdminUserResetPasswordRequest request) {
    try {
      adminUserService.resetPassword(id, request.getNewPassword());
      return ResponseEntity.ok(Map.of("message", "密码已重置"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

}
