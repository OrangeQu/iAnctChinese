package com.ianctchinese.service;

import com.ianctchinese.dto.AuthRequest;
import com.ianctchinese.dto.AuthResponse;
import com.ianctchinese.dto.RegisterRequest;
import com.ianctchinese.dto.UserInfoResponse;
import com.ianctchinese.model.User;
import com.ianctchinese.repository.UserRepository;
import com.ianctchinese.security.JwtUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final AuthenticationManager authenticationManager;

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
      throw new IllegalArgumentException("用户名已存在");
    }

    if (userRepository.existsByEmail(request.getEmail())) {
      throw new IllegalArgumentException("邮箱已被注册");
    }

    User user = User.builder()
        .username(request.getUsername())
        .email(request.getEmail())
        .password(passwordEncoder.encode(request.getPassword()))
        .createTime(LocalDateTime.now())
        .enabled(true)
        .build();

    userRepository.save(user);

    String token = jwtUtil.generateToken(user.getUsername());

    return AuthResponse.builder()
        .token(token)
        .username(user.getUsername())
        .email(user.getEmail())
        .message("注册成功")
        .build();
  }

  @Transactional
  public AuthResponse login(AuthRequest request) {
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.getUsername(),
            request.getPassword()
        )
    );

    User user = userRepository.findByUsername(request.getUsername())
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

    user.setLastLoginTime(LocalDateTime.now());
    userRepository.save(user);

    String token = jwtUtil.generateToken(user.getUsername());

    return AuthResponse.builder()
        .token(token)
        .username(user.getUsername())
        .email(user.getEmail())
        .message("登录成功")
        .build();
  }

  public UserInfoResponse getCurrentUser(String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

    return UserInfoResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .createTime(user.getCreateTime())
        .lastLoginTime(user.getLastLoginTime())
        .build();
  }
}
