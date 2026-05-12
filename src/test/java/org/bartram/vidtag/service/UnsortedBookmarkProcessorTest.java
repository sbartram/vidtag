package org.bartram.vidtag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.bartram.vidtag.config.UnsortedProcessorProperties;
import org.bartram.vidtag.event.VideoProcessedEvent;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.ProcessingStatus;
import org.bartram.vidtag.model.Raindrop;
import org.bartram.vidtag.model.Source;
import org.bartram.vidtag.model.TagWithConfidence;
import org.bartram.vidtag.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UnsortedBookmarkProcessorTest {

    @Mock
    private RaindropService raindropService;

    @Mock
    private YouTubeService youtubeService;

    @Mock
    private VideoTaggingService videoTaggingService;

    @Mock
    private CollectionSelectionService collectionSelectionService;

    @Mock
    private UnsortedProcessorProperties properties;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UnsortedBookmarkProcessor processor;

    @Test
    void processUnsortedBookmarks_successPath_publishesSuccessEvent() {
        Raindrop raindrop = new Raindrop(1L, "https://www.youtube.com/watch?v=abc", "Golf Swing Tips");
        VideoMetadata video = new VideoMetadata(
                "abc",
                "https://www.youtube.com/watch?v=abc",
                "Golf Swing Tips",
                "A video about golf swings",
                Instant.now(),
                300);

        when(properties.isEnabled()).thenReturn(true);
        when(raindropService.getUnsortedRaindrops()).thenReturn(List.of(raindrop));
        when(youtubeService.extractVideoId(raindrop.link())).thenReturn("abc");
        when(raindropService.getUserTags(anyString())).thenReturn(List.of());
        when(youtubeService.getVideoMetadata("abc")).thenReturn(video);
        when(videoTaggingService.generateTags(eq(video), anyList(), any()))
                .thenReturn(List.of(new TagWithConfidence("golf", 0.9, false)));
        when(collectionSelectionService.selectCollectionForVideo(video)).thenReturn("Videos");
        when(raindropService.resolveCollectionId(anyString(), eq("Videos"))).thenReturn(42L);

        processor.processUnsortedBookmarks();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        List<VideoProcessedEvent> events = captor.getAllValues().stream()
                .filter(VideoProcessedEvent.class::isInstance)
                .map(VideoProcessedEvent.class::cast)
                .toList();

        assertThat(events).hasSize(1);
        ProcessedVideoEntry entry = events.get(0).entry();
        assertThat(entry.source()).isEqualTo(Source.UNSORTED);
        assertThat(entry.status()).isEqualTo(ProcessingStatus.SUCCESS);
        assertThat(entry.collection()).isEqualTo("Videos");
        assertThat(entry.tags()).containsExactly("golf");
    }
}
