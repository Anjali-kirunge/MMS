package com.military.ams.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a stock movement would drive a base's on-hand quantity below zero,
 * or when a request otherwise violates a data-integrity rule (for example a
 * transfer whose source and destination base are the same).
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.CONFLICT, message);
    }

    public static BusinessRuleException insufficientStock(String base, String equipment, int requested, int available) {
        return new BusinessRuleException(
                "Insufficient stock at " + base + " for " + equipment + ": requested " + requested
                        + ", available " + available + ". Stock cannot go negative.");
    }
}
