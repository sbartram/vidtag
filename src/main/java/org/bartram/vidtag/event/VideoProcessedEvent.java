package org.bartram.vidtag.event;

import org.bartram.vidtag.model.ProcessedVideoEntry;

/**
 * Spring application event published by pipelines after a single video is
 * processed. A POJO record published via
 * {@code ApplicationEventPublisher.publishEvent(Object)} — Spring 6+ supports
 * non-{@code ApplicationEvent} payloads.
 */
public record VideoProcessedEvent(ProcessedVideoEntry entry) {}
