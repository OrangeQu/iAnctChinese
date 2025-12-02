package com.ianctchinese.service.impl;

import com.ianctchinese.dto.UserCreateRequest;
import com.ianctchinese.dto.UserStatusUpdateRequest;
import com.ianctchinese.dto.UserSummaryResponse;
import com.ianctchinese.model.User;
import com.ianctchinese.repository.UserRepository;
import com.ianctchinese.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional(readOnly = true)
  public List<UserSummaryResponse> listUsers() {
    return userRepository.findAll().stream()
        .map(this::toSummary)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public UserSummaryResponse createUser(UserCreateRequest request) {
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
    User saved = userRepository.save(user);
    return toSummary(saved);
  }

  @Override
  @Transactional
  public UserSummaryResponse updateStatus(Long userId, UserStatusUpdateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    user.setEnabled(request.getEnabled());
    User saved = userRepository.save(user);
    return toSummary(saved);
  }

  private UserSummaryResponse toSummary(User user) {
    return UserSummaryResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .enabled(user.getEnabled())
        .createTime(user.getCreateTime())
        .lastLoginTime(user.getLastLoginTime())
        .build();
  }
}
