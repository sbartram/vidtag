package org.bartram.vidtag.model;

/**
 * Summary of playlist processing results.
 *
 * @param totalVideos total number of videos in playlist
 * @param succeeded number of successfully processed videos
 * @param skipped number of skipped videos
 * @param failed number of failed videos
 */
public record ProcessingSummary(Integer totalVideos, Integer succeeded, Integer skipped, Integer failed) {}
