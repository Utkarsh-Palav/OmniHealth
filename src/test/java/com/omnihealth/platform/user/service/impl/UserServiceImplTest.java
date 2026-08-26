package com.omnihealth.platform.user.service.impl;

import com.omnihealth.common.enums.UserStatus;
import com.omnihealth.platform.user.dto.request.CreateUserRequest;
import com.omnihealth.platform.user.dto.response.UserResponse;
import com.omnihealth.platform.user.service.UserService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Integration test requiring live PostgreSQL container on localhost:5432")
class UserServiceImplTest {

    @Autowired
    private UserService userService;

    @Test
    void testCreateUserSuccess() {
        String uniqueEmail = "test-" + UUID.randomUUID() + "@example.com";
        CreateUserRequest request = new CreateUserRequest(
                "John",
                null,
                "Doe",
                null,
                uniqueEmail,
                null,
                null,
                null,
                "Password123!"
        );

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals(uniqueEmail, response.email());
        assertEquals("John", response.firstName());
        assertEquals("Doe", response.lastName());
        assertEquals(UserStatus.PENDING_EMAIL_VERIFICATION, response.status());
    }
}
