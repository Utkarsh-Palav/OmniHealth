package com.omnihealth.platform.user.dto.request;

import com.omnihealth.common.enums.UserStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Pattern(regexp = "^[+]?[0-9]{7,20}$")
        String phone,

        @Size(max = 500)
        String avatarUrl,

        UserStatus status

) {}