package com.omnihealth.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * Standard API response wrapper used across the entire application.
 *
 * @param <T> Response payload type
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success; // Indicate weather the request was successful.
    private String code;
    private String message; // Human-readable message
    private T data; // Actual response payload.
    private List<ApiError> errors; // List of error (used only for failed requests).
    private PaginationMeta pagination; // Pagination information

    /**
     * Additional Metadata
     */
    @Builder.Default
    private ApiMeta meta = ApiMeta.builder().build();
}