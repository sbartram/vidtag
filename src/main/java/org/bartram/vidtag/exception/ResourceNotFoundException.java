package org.bartram.vidtag.exception;

/**
 * Exception thrown when a requested resource is not found.
 * Returns HTTP 404 status.
 */
public class ResourceNotFoundException extends VidtagException {

    public ResourceNotFoundException(String resource, String identifier) {
        super("RESOURCE_NOT_FOUND",
              String.format("%s '%s' not found", resource, identifier),
              404);
    }

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message, 404);
    }
}
