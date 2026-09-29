package com.military.ams.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all business errors surfaced to the API.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
