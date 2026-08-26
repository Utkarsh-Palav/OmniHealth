package com.omnihealth.platform.onboarding.dto.request;

import com.omnihealth.platform.billing.enums.PaymentProvider;
import jakarta.validation.constraints.Size;

/**
 * Optional payment-confirmation payload for the (optional) PAYMENT step.
 *
 * <p>
 * Intentionally minimal — the full payments and invoicing domain is deferred
 * to a dedicated billing module. When supplied, the provider details are
 * reflected onto the organization's active subscription.
 * </p>
 */
public record CompletePaymentRequest(

        PaymentProvider provider,

        @Size(max = 255)
        String providerReference
) {
}
