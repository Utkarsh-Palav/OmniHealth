package com.omnihealth.common.exception;

import com.omnihealth.common.response.ApiError;
import com.omnihealth.common.response.ApiResponse;
import com.omnihealth.common.builder.ApiResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.List;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiResponseBuilder apiResponseBuilder;

    /**
     * Handles all custom business exceptions.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {

        log.warn(
                "Business exception: {}",
                exception.getMessage()
        );

        return ResponseEntity
                .status(exception.getErrorCode().getHttpStatus())
                .body(
                        apiResponseBuilder.error(
                                exception,
                                request
                        )
                );
    }

    /**
     * Handles Bean Validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {

        List<ApiError> errors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .toList();

        return ResponseEntity
                .badRequest()
                .body(
                        apiResponseBuilder.validationError(
                                errors,
                                CommonErrorCode.VALIDATION_FAILED,
                                request
                        )
                );
    }

    /**
     * Handles JSON deserialization errors (invalid enums, malformed JSON, etc.).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {

        Throwable cause = exception.getMostSpecificCause();

        if (cause instanceof InvalidFormatException invalidFormatException
                && invalidFormatException.getTargetType().isEnum()) {

            String field = invalidFormatException.getPath().isEmpty()
                    ? "unknown"
                    : invalidFormatException.getPath().getFirst().getPropertyName();

            String allowedValues = Arrays.stream(
                            invalidFormatException.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .toList()
                    .toString();

            ApiError error = ApiError.builder()
                    .field(field)
                    .message("must be one of " + allowedValues)
                    .build();

            return ResponseEntity.badRequest().body(
                    apiResponseBuilder.validationError(
                            List.of(error),
                            CommonErrorCode.VALIDATION_FAILED,
                            request
                    )
            );
        }

        ApiError error = ApiError.builder()
                .field("request")
                .message("Malformed JSON request.")
                .build();

        return ResponseEntity.badRequest().body(
                apiResponseBuilder.validationError(
                        List.of(error),
                        CommonErrorCode.VALIDATION_FAILED,
                        request
                )
        );
    }

    /**
     * Handles unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {

        log.error(
                "Unhandled exception",
                exception
        );

        return ResponseEntity
                .internalServerError()
                .body(
                        apiResponseBuilder.error(
                                CommonErrorCode.INTERNAL_SERVER_ERROR,
                                request
                        )
                );
    }

    private ApiError mapFieldError(FieldError fieldError) {

        return ApiError.builder()
                .field(fieldError.getField())
                .message(fieldError.getDefaultMessage())
                .build();
    }
}