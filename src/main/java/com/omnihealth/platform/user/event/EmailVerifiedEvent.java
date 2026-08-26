package com.omnihealth.platform.user.event;

import java.util.UUID;

/**
 * Published within the email-verification transaction once a user's account
 * has been marked {@code ACTIVE}.
 *
 * <p>
 * This is a user-domain event: the user module (and the auth module, which
 * already depends on it) owns and publishes it, while other modules — such as
 * onboarding — may react to it. Keeping it here preserves the decoupling
 * between auth and onboarding.
 * </p>
 */
public record EmailVerifiedEvent(UUID userId) {
}
