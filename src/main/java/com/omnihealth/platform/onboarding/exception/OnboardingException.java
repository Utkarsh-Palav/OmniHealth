package com.omnihealth.platform.onboarding.exception;

import com.omnihealth.common.exception.BusinessException;

/**
 * Raised when an onboarding operation violates a business rule, such as an
 * illegal state transition or a misconfigured default trial plan.
 */
public class OnboardingException extends BusinessException {

    public OnboardingException(OnboardingErrorCode errorCode) {
        super(errorCode);
    }

    public OnboardingException(OnboardingErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
