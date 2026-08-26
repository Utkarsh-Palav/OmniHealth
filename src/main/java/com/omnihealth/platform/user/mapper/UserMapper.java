package com.omnihealth.platform.user.mapper;

import com.omnihealth.platform.user.dto.request.CreateUserRequest;
import com.omnihealth.platform.user.dto.request.UpdateUserRequest;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

    // =========================================================
    // CREATE
    // =========================================================

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)

    // System-managed fields
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "userStatus", ignore = true)
    @Mapping(target = "emailVerifiedAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)

    User toEntity(CreateUserRequest request);


    // =========================================================
    // RESPONSE
    // =========================================================

    @Mapping(target = "status", source = "userStatus")
    UserResponse toResponse(User user);


    // =========================================================
    // UPDATE
    // =========================================================

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)

    // Immutable / security-managed fields
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "userStatus", ignore = true)
    @Mapping(target = "emailVerifiedAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "lockedUntil", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)

    void updateEntity(
            UpdateUserRequest request,
            @MappingTarget User user
    );
}