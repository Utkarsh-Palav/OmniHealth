package com.omnihealth.platform.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank
    @Size(max = 100)
    String firstName,

    @Size(max = 100)
    String lastName,

    @NotBlank
    @Size(max = 225)
    String email,

    @Pattern(regexp = "^[+]?[0-9]{7,20}$")
    String phone,

    @Size(max = 500)
    String avatarUrl,

    @NotBlank
    @Size(min = 8, max = 100)
    String password
) {
}
