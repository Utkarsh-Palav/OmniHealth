package com.omnihealth.platform.user.service.impl;

import com.omnihealth.common.enums.AuthProvider;
import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.common.exception.ConflictException;
import com.omnihealth.common.exception.DuplicateResourceException;
import com.omnihealth.common.exception.ResourceNotFoundException;
import com.omnihealth.platform.user.dto.request.CreateUserRequest;
import com.omnihealth.platform.user.dto.request.UpdateUserRequest;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.entity.User;
import com.omnihealth.platform.user.mapper.UserMapper;
import com.omnihealth.platform.user.repository.UserRepository;
import com.omnihealth.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse createUser(CreateUserRequest request) {

        log.info("Creating user with email={}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", "email", request.email());
        }

        User user = userMapper.toEntity(request);

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.PENDING);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);

        User savedUser = userRepository.save(user);

        log.info("User created successfully with id={}", savedUser.getId());

        return userMapper.toResponse(savedUser);

    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {

        User user = getUserOrThrow(userId);

        return userMapper.toResponse(user);

    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Pageable pageable) {

        return userRepository
                .findAllByDeletedAtIsNull(pageable)
                .map(userMapper::toResponse);
    }

    @Override
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {

        User user = getUserOrThrow(userId);

        userMapper.updateEntity(request, user);

        User updatedUser = userRepository.save(user);

        log.info("User updated successfully. userId={}", updatedUser.getId());

        return userMapper.toResponse(updatedUser);
    }

    @Override
    public void disableUser(UUID userId) {

        User user = getUserOrThrow(userId);

        if (user.getStatus() == UserStatus.DISABLED) {
            throw new ConflictException(
                    "User is already disabled."
            );
        }

        user.setStatus(UserStatus.DISABLED);
        user.setDeletedAt(Instant.now());

        userRepository.save(user);

        log.info("User disabled successfully. userId={}", userId);

    }

    private User getUserOrThrow(UUID userId) {

        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User", "id", userId.toString()
                        )
                );
    }
}
