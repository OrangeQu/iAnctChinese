package com.ianctchinese.controller;

import com.ianctchinese.dto.AuthRequest;
import com.ianctchinese.dto.AuthResponse;
import com.ianctchinese.dto.RegisterRequest;
import com.ianctchinese.dto.UserInfoResponse;
import com.ianctchinese.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    try {
      AuthResponse response = authService.register(request);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .body(AuthResponse.builder().message(e.getMessage()).build());
    }
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
    try {
      AuthResponse response = authService.login(request);
      return ResponseEntity.ok(response);
    } catch (Exception e) {
      return ResponseEntity.badRequest()
          .body(AuthResponse.builder().message("用户名或密码错误").build());
    }
  }

  @GetMapping("/current")
  public ResponseEntity<UserInfoResponse> getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    String username = authentication.getName();
    UserInfoResponse response = authService.getCurrentUser(username);
    return ResponseEntity.ok(response);
  }
}
