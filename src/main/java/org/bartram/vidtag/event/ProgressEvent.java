package org.bartram.vidtag.event;

/**
 * SSE event model for video tagging progress updates.
 *
 * @param eventType the type of event (started, progress, videoCompleted, videoSkipped, batchCompleted, error, completed)
 * @param message human-readable message describing the event
 * @param data additional event data (e.g., VideoProcessingResult, ProcessingSummary)
 */
public record ProgressEvent(String eventType, String message, Object data) {

    /**
     * Creates a "started" event indicating processing has begun.
     *
     * @param message description of what is starting
     * @return started progress event
     */
    public static ProgressEvent started(String message) {
        return new ProgressEvent("started", message, null);
    }

    /**
     * Creates a "progress" event for general progress updates.
     *
     * @param message progress description
     * @return progress event
     */
    public static ProgressEvent progress(String message) {
        return new ProgressEvent("progress", message, null);
    }

    /**
     * Creates a "progress" event with additional data.
     *
     * @param message progress description
     * @param data additional progress data
     * @return progress event
     */
    public static ProgressEvent progress(String message, Object data) {
        return new ProgressEvent("progress", message, data);
    }

    /**
     * Creates a "videoCompleted" event when a video has been successfully processed.
     *
     * @param message completion message
     * @param data video processing result
     * @return video completed event
     */
    public static ProgressEvent videoCompleted(String message, Object data) {
        return new ProgressEvent("videoCompleted", message, data);
    }

    /**
     * Creates a "videoSkipped" event when a video is skipped (e.g., duplicate).
     *
     * @param message skip reason
     * @param data video processing result
     * @return video skipped event
     */
    public static ProgressEvent videoSkipped(String message, Object data) {
        return new ProgressEvent("videoSkipped", message, data);
    }

    /**
     * Creates a "batchCompleted" event when a batch of videos has been processed.
     *
     * @param message batch completion message
     * @param data batch statistics
     * @return batch completed event
     */
    public static ProgressEvent batchCompleted(String message, Object data) {
        return new ProgressEvent("batchCompleted", message, data);
    }

    /**
     * Creates an "error" event when an error occurs during processing.
     *
     * @param message error description
     * @return error event
     */
    public static ProgressEvent error(String message) {
        return new ProgressEvent("error", message, null);
    }

    /**
     * Creates an "error" event with additional error data.
     *
     * @param message error description
     * @param data error details
     * @return error event
     */
    public static ProgressEvent error(String message, Object data) {
        return new ProgressEvent("error", message, data);
    }

    /**
     * Creates a "completed" event when all processing is finished.
     *
     * @param message completion message
     * @param data processing summary
     * @return completed event
     */
    public static ProgressEvent completed(String message, Object data) {
        return new ProgressEvent("completed", message, data);
    }
}
