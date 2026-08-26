package com.omnihealth.platform.onboarding.listener;

import com.omnihealth.platform.onboarding.service.OnboardingService;
import com.omnihealth.platform.user.event.EmailVerifiedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OnboardingEventListenerTest {

    @Mock
    private OnboardingService onboardingService;

    @InjectMocks
    private OnboardingEventListener listener;

    @Test
    void forwardsEventUserIdToOnboardingService() {
        UUID userId = UUID.randomUUID();

        listener.onEmailVerified(new EmailVerifiedEvent(userId));

        verify(onboardingService).onEmailVerified(userId);
    }

    @Test
    void isAThinBridgeThatDoesNotThrow() {
        // The listener runs inside the verification transaction, so it must not
        // add behavior of its own that could roll it back — it delegates
        // unconditionally to the (defensive) service.
        UUID userId = UUID.randomUUID();

        assertDoesNotThrow(() -> listener.onEmailVerified(new EmailVerifiedEvent(userId)));
        verify(onboardingService).onEmailVerified(userId);
    }
}
