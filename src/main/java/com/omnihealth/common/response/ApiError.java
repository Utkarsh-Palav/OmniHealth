package com.omnihealth.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represent single API error.
 * Example:
 * - Validation errors
 * - Business rule violations
 * - Field specific errors
 *
 */

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    /**
     * Name of the field that caused the error.
     * Example: "email"
     */
    private String field;

    /**
     * Human-readable error message
     * Example: "Email is already registered."
     */
    private String message;
}
