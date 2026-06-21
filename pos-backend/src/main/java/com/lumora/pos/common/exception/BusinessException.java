package com.lumora.pos.common.exception;

/**
 * Thrown when a business rule is violated.
 * Examples: insufficient stock, invalid discount, duplicate SKU.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
