package org.bartram.vidtag.service;

import org.bartram.vidtag.config.SchedulerProperties;
import org.bartram.vidtag.dto.TagPlaylistRequest;
import org.bartram.vidtag.model.TagStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaylistProcessingSchedulerTest {

    @Mock
    private YouTubeService youtubeService;

    @Mock
    private VideoTaggingOrchestrator orchestrator;

    @Mock
    private SchedulerProperties schedulerProperties;

    @Captor
    private ArgumentCaptor<TagPlaylistRequest> requestCaptor;

    private PlaylistProcessingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PlaylistProcessingScheduler(
            youtubeService,
            orchestrator,
            schedulerProperties
        );
    }

    @Test
    void shouldProcessPlaylistWhenEnabled() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn("PLxyz123");

        scheduler.processTagPlaylist();

        verify(orchestrator).processPlaylist(requestCaptor.capture(), any());

        TagPlaylistRequest request = requestCaptor.getValue();
        assertThat(request.playlistInput()).isEqualTo("PLxyz123");
        assertThat(request.raindropCollectionTitle()).isEqualTo("Videos");
        assertThat(request.filters()).isNotNull();
        assertThat(request.tagStrategy()).isEqualTo(TagStrategy.SUGGEST);
        assertThat(request.verbosity()).isNull();
    }

    @Test
    void shouldSkipProcessingWhenDisabled() {
        when(schedulerProperties.isEnabled()).thenReturn(false);

        scheduler.processTagPlaylist();

        verify(orchestrator, never()).processPlaylist(any(), any());
    }

    @Test
    void shouldContinueOnException() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn("PLxyz123");
        doThrow(new RuntimeException("API error"))
            .when(orchestrator).processPlaylist(any(), any());

        // Should not throw exception
        scheduler.processTagPlaylist();

        verify(orchestrator).processPlaylist(any(), any());
    }

    @Test
    void shouldSkipWhenPlaylistIdIsEmpty() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn("");

        scheduler.processTagPlaylist();

        verify(orchestrator, never()).processPlaylist(any(), any());
    }

    @Test
    void shouldSkipWhenPlaylistIdIsNull() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn(null);

        scheduler.processTagPlaylist();

        verify(orchestrator, never()).processPlaylist(any(), any());
    }

    @Test
    void shouldProcessSinglePlaylist() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn("PLsingle123");

        scheduler.processTagPlaylist();

        verify(orchestrator, times(1)).processPlaylist(requestCaptor.capture(), any());

        TagPlaylistRequest request = requestCaptor.getValue();
        assertThat(request.playlistInput()).isEqualTo("PLsingle123");
    }

    @Test
    void shouldProcessMultiplePlaylistsSequentially() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn("PLfirst,PLsecond,PLthird");

        scheduler.processTagPlaylist();

        verify(orchestrator, times(3)).processPlaylist(requestCaptor.capture(), any());

        var requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).playlistInput()).isEqualTo("PLfirst");
        assertThat(requests.get(1).playlistInput()).isEqualTo("PLsecond");
        assertThat(requests.get(2).playlistInput()).isEqualTo("PLthird");
    }

    @Test
    void shouldContinueProcessingWhenOnePlaylistFails() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn("PLgood1,PLbad,PLgood2");

        // Throw exception only for the second playlist
        doNothing()
            .doThrow(new RuntimeException("API error"))
            .doNothing()
            .when(orchestrator).processPlaylist(any(), any());

        scheduler.processTagPlaylist();

        // All three playlists should be attempted
        verify(orchestrator, times(3)).processPlaylist(requestCaptor.capture(), any());

        var requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).playlistInput()).isEqualTo("PLgood1");
        assertThat(requests.get(1).playlistInput()).isEqualTo("PLbad");
        assertThat(requests.get(2).playlistInput()).isEqualTo("PLgood2");
    }

    @Test
    void shouldHandleWhitespaceInPlaylistIds() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn("PLfirst, PLsecond , PLthird");

        scheduler.processTagPlaylist();

        verify(orchestrator, times(3)).processPlaylist(requestCaptor.capture(), any());

        var requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).playlistInput()).isEqualTo("PLfirst");
        assertThat(requests.get(1).playlistInput()).isEqualTo("PLsecond");
        assertThat(requests.get(2).playlistInput()).isEqualTo("PLthird");
    }

    @Test
    void shouldSkipEmptyPlaylistIds() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn("PLfirst,,PLsecond, ,PLthird");

        scheduler.processTagPlaylist();

        verify(orchestrator, times(3)).processPlaylist(requestCaptor.capture(), any());

        var requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).playlistInput()).isEqualTo("PLfirst");
        assertThat(requests.get(1).playlistInput()).isEqualTo("PLsecond");
        assertThat(requests.get(2).playlistInput()).isEqualTo("PLthird");
    }

    @Test
    void shouldSkipWhenAllPlaylistIdsAreEmpty() {
        when(schedulerProperties.isEnabled()).thenReturn(true);
        when(schedulerProperties.getPlaylistIds()).thenReturn("  ,  ,  ");

        scheduler.processTagPlaylist();

        verify(orchestrator, never()).processPlaylist(any(), any());
    }
}
