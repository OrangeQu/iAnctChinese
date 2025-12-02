package com.ianctchinese.service;

import com.ianctchinese.dto.UserCreateRequest;
import com.ianctchinese.dto.UserStatusUpdateRequest;
import com.ianctchinese.dto.UserSummaryResponse;
import java.util.List;

public interface UserService {

  List<UserSummaryResponse> listUsers();

  UserSummaryResponse createUser(UserCreateRequest request);

  UserSummaryResponse updateStatus(Long userId, UserStatusUpdateRequest request);
}
