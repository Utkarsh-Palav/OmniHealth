package com.omnihealth.common.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Contract for all application error codes.
 */
public interface BaseErrorCode {
    String getCode();

    HttpStatusCode getHttpStatus();

    String getDefaultMessage();
}