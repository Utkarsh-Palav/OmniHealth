package com.omnihealth.platform.onboarding.listener;

import com.omnihealth.platform.onboarding.service.OnboardingService;
import com.omnihealth.platform.user.event.EmailVerifiedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bridges the user-domain {@link EmailVerifiedEvent} to the onboarding module.
 *
 * <p>
 * The listener is synchronous and therefore runs inside the same transaction as
 * the email-verification flow that publishes the event. This keeps the session
 * transition atomic with verification. {@link OnboardingService#onEmailVerified}
 * is intentionally defensive and never throws, so it cannot roll back the
 * verification.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardingEventListener {

    private final OnboardingService onboardingService;

    @EventListener
    public void onEmailVerified(EmailVerifiedEvent event) {
        log.debug("Received EmailVerifiedEvent for userId={}", event.userId());
        onboardingService.onEmailVerified(event.userId());
    }
}
