package com.ianctchinese.service;

import com.ianctchinese.dto.AdminUserResponse;
import com.ianctchinese.model.User;
import com.ianctchinese.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public Page<AdminUserResponse> listUsers(String query, Boolean enabled, Pageable pageable) {
    Page<User> page = userRepository.searchUsers(query, enabled, pageable);
    return page.map(this::toResponse);
  }

  @Transactional
  public void updateEnabled(Long id, boolean enabled) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    if ("admin".equalsIgnoreCase(user.getUsername()) && !enabled) {
      throw new IllegalArgumentException("不能禁用管理员账号");
    }
    user.setEnabled(enabled);
    userRepository.save(user);
  }

  @Transactional
  public void resetPassword(Long id, String newPassword) {
    if (newPassword == null || newPassword.trim().length() < 6) {
      throw new IllegalArgumentException("新密码长度至少为6位");
    }
    User user = userRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    user.setPassword(passwordEncoder.encode(newPassword.trim()));
    userRepository.save(user);
  }

  private AdminUserResponse toResponse(User user) {
    return AdminUserResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .enabled(user.getEnabled())
        .createTime(user.getCreateTime())
        .lastLoginTime(user.getLastLoginTime())
        .build();
  }
}
