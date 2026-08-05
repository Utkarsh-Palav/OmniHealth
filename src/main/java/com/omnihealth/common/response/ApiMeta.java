package com.omnihealth.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metadata associated with every API response.
 * <p>
 * This class provides contextual information about
 * the request without polluting the actual response data.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiMeta {

    /**
     * Time when response was generated
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * API endpoint that handled the request.
     *
     * Example:
     * /api/v1/patients
     */
    private String path;
}
