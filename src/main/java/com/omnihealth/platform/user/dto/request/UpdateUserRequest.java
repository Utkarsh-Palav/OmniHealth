package com.omnihealth.platform.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String middleName,

        @Size(max = 100)
        String lastName,

        @Size(max = 100)
        String preferredName,

        @Size(max = 5)
        @Pattern(
                regexp = "^\\+?[0-9]{1,4}$",
                message = "Invalid phone country code"
        )
        String phoneCountryCode,

        @Pattern(
                regexp = "^[0-9]{7,20}$",
                message = "Phone number must contain 7 to 20 digits"
        )
        String phoneNumber,

        @Size(max = 512)
        String profileImageKey

) {
}