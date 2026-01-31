package org.bartram.vidtag.exception;

/**
 * Base exception for all VidTag business errors.
 * Provides structured error information including error code and HTTP status.
 */
public abstract class VidtagException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    protected VidtagException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected VidtagException(String errorCode, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
