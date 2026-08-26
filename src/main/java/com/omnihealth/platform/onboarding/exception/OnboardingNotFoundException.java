package com.omnihealth.platform.onboarding.exception;

import com.omnihealth.common.exception.BusinessException;

/**
 * Raised when an onboarding session cannot be located.
 */
public class OnboardingNotFoundException extends BusinessException {

    public OnboardingNotFoundException() {
        super(OnboardingErrorCode.ONBOARDING_SESSION_NOT_FOUND);
    }

    public OnboardingNotFoundException(String message) {
        super(OnboardingErrorCode.ONBOARDING_SESSION_NOT_FOUND, message);
    }
}
