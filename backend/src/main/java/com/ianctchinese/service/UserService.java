package com.ianctchinese.service;

import com.ianctchinese.dto.UpdateEmailRequest;
import com.ianctchinese.dto.UpdatePasswordRequest;
import com.ianctchinese.dto.UserInfoResponse;
import com.ianctchinese.model.User;
import com.ianctchinese.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserInfoResponse getProfile(String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    return toResponse(user);
  }

  @Transactional
  public UserInfoResponse updateEmail(String username, UpdateEmailRequest request) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

    boolean emailTaken = userRepository.existsByEmail(request.getEmail())
        && !request.getEmail().equals(user.getEmail());
    if (emailTaken) {
      throw new IllegalArgumentException("邮箱已被占用");
    }

    user.setEmail(request.getEmail());
    userRepository.save(user);
    return toResponse(user);
  }

  @Transactional
  public void changePassword(String username, UpdatePasswordRequest request) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
      throw new IllegalArgumentException("当前密码不正确");
    }

    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
    userRepository.save(user);
  }

  private UserInfoResponse toResponse(User user) {
    return UserInfoResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .createTime(user.getCreateTime())
        .lastLoginTime(user.getLastLoginTime())
        .build();
  }
}
