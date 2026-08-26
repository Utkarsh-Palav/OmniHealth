package com.omnihealth.platform.user.controller;

import com.omnihealth.common.builder.ApiResponseBuilder;
import com.omnihealth.common.constants.ApiRoutes;
import com.omnihealth.common.response.ApiResponse;
import com.omnihealth.platform.user.dto.request.CreateUserRequest;
import com.omnihealth.platform.user.dto.request.UpdateUserRequest;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiRoutes.USERS)
@RequiredArgsConstructor
@Validated
@Tag(name = "Users", description = "User Management APIs")
public class UserController {

    private final UserService userService;
    private final ApiResponseBuilder apiResponseBuilder;

    @PostMapping
    @Operation(
            summary = "Create User",
            description = "Creates a new platform user."
    )
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest httpRequest
    ) {

        final UserResponse response = userService.createUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        apiResponseBuilder.created(
                                response,
                                "User created successfully",
                                httpRequest
                        )
                );
    }

    @GetMapping("/verify-email")
    @Operation(
            summary = "Verify User Email",
            description = "Verifies a newly registered user using the provided token."
    )
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam("token") String token,
            HttpServletRequest httpRequest
    ) {
        userService.verifyEmail(token);

        return ResponseEntity.ok(
                apiResponseBuilder.success(
                        null,
                        "Email verified successfully. Your account is now active.",
                        httpRequest
                )
        );
    }

    @GetMapping
    @Operation(
            summary = "Get Users",
            description = "Retrieves a paginated list of users."
    )
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers(
            @ParameterObject Pageable pageable,
            HttpServletRequest httpRequest
    ) {

        final Page<UserResponse> response =
                userService.getUsers(pageable);

        return ResponseEntity.ok(
                apiResponseBuilder.paginated(
                        response,
                        "Users retrieved successfully",
                        httpRequest
                )
        );
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get User",
            description = "Retrieves a user by its unique identifier."
    )
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable("id") UUID userId,
            HttpServletRequest httpRequest
    ) {

        final UserResponse response = userService.getUserById(userId);

        return ResponseEntity.ok(
                apiResponseBuilder.success(response, "User retrieved successfully", httpRequest)
        );
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update User",
            description = "Updates an existing user's information."
    )
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable("id") UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            HttpServletRequest httpRequest
    ) {

        final UserResponse response = userService.updateUser(userId, request);

        return ResponseEntity.ok(
                apiResponseBuilder.success(response, "User updated successfully", httpRequest)
        );
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Disable User",
            description = "Disables a user using a soft delete."
    )
    public ResponseEntity<ApiResponse<Void>> disableUser(
            @PathVariable("id") UUID userId,
            HttpServletRequest httpRequest
    ) {

        userService.disableUser(userId);

        return ResponseEntity.ok(
                apiResponseBuilder.noContent(
                        "User archived successfully.",
                        httpRequest
                )
        );
    }
}
