package com.omnihealth.platform.user.service.impl;

import com.omnihealth.common.enums.TokenType;
import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.common.exception.ConflictException;
import com.omnihealth.common.exception.DuplicateResourceException;
import com.omnihealth.common.exception.ResourceNotFoundException;
import com.omnihealth.platform.user.dto.request.CreateUserRequest;
import com.omnihealth.platform.user.dto.request.UpdateUserRequest;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.entity.User;
import com.omnihealth.platform.user.entity.VerificationToken;
import com.omnihealth.platform.user.event.EmailVerifiedEvent;
import com.omnihealth.platform.user.mapper.UserMapper;
import com.omnihealth.platform.user.repository.UserRepository;
import com.omnihealth.platform.user.repository.VerificationTokenRepository;
import com.omnihealth.platform.user.service.EmailService;
import com.omnihealth.platform.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenRepository verificationTokenRepository;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;

    // -------------------------------------------------
    // Create
    // -------------------------------------------------

    @Override
    public UserResponse createUser(CreateUserRequest request) {

        log.info("Creating user with email={}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "User",
                    "email",
                    request.email()
            );
        }

        User user = userMapper.toEntity(request);

        // Password is always handled by the service.
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );

        // Initial account state.
        user.setUserStatus(
                UserStatus.PENDING_EMAIL_VERIFICATION
        );

        // System-managed security fields.
        user.setEmailVerifiedAt(null);
        user.setFailedLoginAttempts((short) 0);
        user.setLockedUntil(null);
        user.setLastLoginAt(null);

        User savedUser = userRepository.save(user);

        // Generate Raw Token and Hash
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        // Persist Token
        VerificationToken tokenEntity = new VerificationToken();
        tokenEntity.setUser(savedUser);
        tokenEntity.setTokenType(TokenType.EMAIL_VERIFICATION);
        tokenEntity.setTokenHash(tokenHash);
        tokenEntity.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        verificationTokenRepository.save(tokenEntity);

        log.info(
                "User created successfully with id={}",
                savedUser.getId()
        );

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailService.sendVerificationEmail(savedUser.getEmail(), rawToken);
                }
            });
        } else {
            emailService.sendVerificationEmail(savedUser.getEmail(), rawToken);
        }

        return userMapper.toResponse(savedUser);
    }

    // -------------------------------------------------
    // Get by ID
    // -------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {

        User user = getUserOrThrow(userId);

        return userMapper.toResponse(user);
    }

    // -------------------------------------------------
    // Get all users
    // -------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Pageable pageable) {

        return userRepository
                .findAllByDeletedAtIsNull(pageable)
                .map(userMapper::toResponse);
    }

    // -------------------------------------------------
    // Update
    // -------------------------------------------------

    @Override
    public UserResponse updateUser(
            UUID userId,
            UpdateUserRequest request
    ) {

        User user = getUserOrThrow(userId);

        userMapper.updateEntity(request, user);

        User updatedUser = userRepository.save(user);

        log.info(
                "User updated successfully. userId={}",
                updatedUser.getId()
        );

        return userMapper.toResponse(updatedUser);
    }

    // -------------------------------------------------
    // Disable
    // -------------------------------------------------

    @Override
    public void disableUser(UUID userId) {

        User user = getUserOrThrow(userId);

        if (user.getUserStatus() == UserStatus.DELETED) {
            throw new ConflictException(
                    "User is already disabled."
            );
        }

        user.setUserStatus(UserStatus.DELETED);
        user.setDeletedAt(Instant.now());

        userRepository.save(user);

        log.info(
                "User disabled successfully. userId={}",
                userId
        );
    }

    @Override
    public void verifyEmail(String rawToken) {
        String tokenHash = hashToken(rawToken);

        VerificationToken token = verificationTokenRepository
                .findByTokenHashAndTokenTypeAndDeletedAtIsNull(tokenHash, TokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new ResourceNotFoundException("VerificationToken", "token", rawToken));

        if (token.getConsumedAt() != null) {
            throw new ConflictException("Email verification token has been already used.");
        }

        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("Verification token has expired or been revoked.");
        }

        // Update token state
        token.setConsumedAt(Instant.now());
        verificationTokenRepository.save(token);

        // Update user state
        User user = token.getUser();
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        // Notify interested modules (e.g. onboarding) within this transaction.
        eventPublisher.publishEvent(new EmailVerifiedEvent(user.getId()));

        log.info("User id={} successfully verified their email.", user.getId());
    }

    // -------------------------------------------------
    // Helper
    // -------------------------------------------------
    private User getUserOrThrow(UUID userId) {

        return userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User",
                                "id",
                                userId.toString()
                        )
                );
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing verification token", e);
        }
    }
}