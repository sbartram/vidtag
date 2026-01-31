package org.bartram.vidtag.exception;

/**
 * Exception thrown when data parsing fails (JSON, duration formats, etc).
 * Returns HTTP 500 status.
 */
public class DataParsingException extends VidtagException {

    public DataParsingException(String message, Throwable cause) {
        super("DATA_PARSING_ERROR", message, 500, cause);
    }
}
