package org.bartram.vidtag.model;

/**
 * Status of video processing in the tagging workflow.
 */
public enum ProcessingStatus {
    /**
     * Video processing completed successfully.
     */
    SUCCESS,

    /**
     * Video was skipped due to filters.
     */
    SKIPPED,

    /**
     * Video processing failed due to an error.
     */
    FAILED
}
