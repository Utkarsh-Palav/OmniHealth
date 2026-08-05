package com.omnihealth.common.builder;

import com.omnihealth.common.exception.BaseErrorCode;
import com.omnihealth.common.exception.BusinessException;
import com.omnihealth.common.response.ApiError;
import com.omnihealth.common.response.ApiMeta;
import com.omnihealth.common.response.ApiResponse;
import com.omnihealth.common.response.PaginationMeta;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApiResponseBuilder {

    /**
     * Creates response metadata.
     */
    private ApiMeta createMeta(HttpServletRequest request) {
        return ApiMeta.builder()
                .path(request.getRequestURI())
                .build();
    }

    private PaginationMeta createPaginationMeta(Page<?> page) {

        return new PaginationMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious()
        );
    }

    /* ==========================================================
       Success Responses
       ========================================================== */

    public <T> ApiResponse<T> success(
            T data,
            String message,
            HttpServletRequest request
    ) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .meta(createMeta(request))
                .build();
    }

    public <T> ApiResponse<T> created(
            T data,
            String message,
            HttpServletRequest request
    ) {
        return success(data, message, request);
    }

    public <T> ApiResponse<T> paginated(
            T data,
            PaginationMeta pagination,
            String message,
            HttpServletRequest request
    ) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .pagination(pagination)
                .meta(createMeta(request))
                .build();
    }

    public <T> ApiResponse<List<T>> paginated(
            Page<T> page,
            String message,
            HttpServletRequest request
    ) {

        return ApiResponse.<List<T>>builder()
                .success(true)
                .message(message)
                .data(page.getContent())
                .pagination(createPaginationMeta(page))
                .meta(createMeta(request))
                .build();
    }

    public ApiResponse<Void> noContent(
            String message,
            HttpServletRequest request
    ) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .meta(createMeta(request))
                .build();
    }

    /* ==========================================================
       Error Responses
       ========================================================== */

    public ApiResponse<Void> error(
            BaseErrorCode errorCode,
            String message,
            HttpServletRequest request
    ) {
        return ApiResponse.<Void>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(message)
                .meta(createMeta(request))
                .build();
    }

    public ApiResponse<Void> error(
            BaseErrorCode errorCode,
            String message,
            List<ApiError> errors,
            HttpServletRequest request
    ) {
        return ApiResponse.<Void>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(message)
                .errors(errors)
                .meta(createMeta(request))
                .build();
    }

    public ApiResponse<Void> error(
            BaseErrorCode errorCode,
            HttpServletRequest request
    ) {
        return error(
                errorCode,
                errorCode.getDefaultMessage(),
                request
        );
    }

    public ApiResponse<Void> error(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return error(
                exception.getErrorCode(),
                exception.getMessage(),
                request
        );
    }

    public ApiResponse<Void> validationError(
            List<ApiError> errors,
            BaseErrorCode errorCode,
            HttpServletRequest request
    ) {
        return error(
                errorCode,
                errorCode.getDefaultMessage(),
                errors,
                request
        );
    }
}