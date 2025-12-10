package com.ianctchinese.controller;

import com.ianctchinese.dto.UpdateEmailRequest;
import com.ianctchinese.dto.UpdatePasswordRequest;
import com.ianctchinese.dto.UserInfoResponse;
import com.ianctchinese.service.UserService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<UserInfoResponse> getProfile(Authentication authentication) {
    String username = authentication.getName();
    UserInfoResponse response = userService.getProfile(username);
    return ResponseEntity.ok(response);
  }

  @PutMapping("/email")
  public ResponseEntity<?> updateEmail(
      @Valid @RequestBody UpdateEmailRequest request,
      Authentication authentication) {
    try {
      String username = authentication.getName();
      UserInfoResponse response = userService.updateEmail(username, request);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }

  @PutMapping("/password")
  public ResponseEntity<?> changePassword(
      @Valid @RequestBody UpdatePasswordRequest request,
      Authentication authentication) {
    try {
      String username = authentication.getName();
      userService.changePassword(username, request);
      return ResponseEntity.ok(Map.of("message", "密码已更新"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
  }
}
