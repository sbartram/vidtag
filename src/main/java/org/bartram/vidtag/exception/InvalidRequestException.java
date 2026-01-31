package org.bartram.vidtag.exception;

/**
 * Exception thrown for invalid request parameters or business logic violations.
 * Returns HTTP 400 status.
 */
public class InvalidRequestException extends VidtagException {

    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message, 400);
    }
}
