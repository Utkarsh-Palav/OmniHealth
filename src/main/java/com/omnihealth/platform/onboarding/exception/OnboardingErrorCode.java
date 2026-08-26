package com.omnihealth.platform.onboarding.exception;

import com.omnihealth.common.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Error codes specific to the organization onboarding / signup workflow.
 */
@Getter
public enum OnboardingErrorCode implements BaseErrorCode {

    ONBOARDING_SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Onboarding session not found"
    ),

    ONBOARDING_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "An active onboarding session already exists for this organization"
    ),

    INVALID_ONBOARDING_STATE(
            HttpStatus.CONFLICT,
            "The onboarding session is not in a valid state for this operation"
    ),

    DEFAULT_TRIAL_PLAN_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The default trial plan is not configured"
    ),

    ORGANIZATION_CODE_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to generate a unique organization code"
    );

    private final HttpStatus httpStatus;

    private final String defaultMessage;

    OnboardingErrorCode(
            HttpStatus httpStatus,
            String defaultMessage
    ) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String getCode() {
        return name();
    }
}
