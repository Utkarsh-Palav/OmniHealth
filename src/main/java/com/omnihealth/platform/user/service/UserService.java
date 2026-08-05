package com.omnihealth.platform.user.service;

import com.omnihealth.platform.user.dto.request.CreateUserRequest;
import com.omnihealth.platform.user.dto.request.UpdateUserRequest;
import com.omnihealth.platform.user.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);

    UserResponse getUserById(UUID id);

    Page<UserResponse> getUsers(Pageable pageable);

    UserResponse updateUser(
            UUID userId,
            UpdateUserRequest request
    );

    void disableUser(UUID userId);
}
