package org.bartram.vidtag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.model.ProcessingStatus;
import org.bartram.vidtag.model.Source;
import org.bartram.vidtag.service.ProcessedVideoRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProcessedVideosControllerTest {

    @Mock
    private ProcessedVideoRecorder recorder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProcessedVideosController(recorder))
                .build();
    }

    @Test
    void get_returnsHtmlContentType() throws Exception {
        when(recorder.recent()).thenReturn(List.of());

        mockMvc.perform(get("/processed"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void titleWithScriptTagIsEscaped() throws Exception {
        when(recorder.recent())
                .thenReturn(List.of(entry("<script>alert(1)</script>", "https://www.youtube.com/watch?v=abc")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(body).doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void nonYoutubeUrl_titleRendersWithoutAnchor() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry("clean", "https://evil.example.com/x")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains(">clean<");
        assertThat(body).doesNotContain("href=\"https://evil.example.com");
    }

    @Test
    void javascriptUrl_titleRendersWithoutAnchor() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry("payload", "javascript:alert(1)")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).doesNotContain("javascript:alert(1)");
        assertThat(body).doesNotContain("href=\"javascript");
    }

    @Test
    void validYoutubeUrl_titleWrappedInAnchor() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry("ok", "https://www.youtube.com/watch?v=abc")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("<a href=\"https://www.youtube.com/watch?v=abc\">ok</a>");
    }

    @Test
    void shortYoutubeUrl_titleWrappedInAnchor() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry("ok", "https://youtu.be/abc")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("<a href=\"https://youtu.be/abc\">ok</a>");
    }

    @Test
    void mobileYoutubeUrl_titleWrappedInAnchor() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry("ok", "https://m.youtube.com/watch?v=abc")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("<a href=\"https://m.youtube.com/watch?v=abc\">ok</a>");
    }

    @Test
    void urlWithUserinfo_titleRendersWithoutAnchor() throws Exception {
        when(recorder.recent()).thenReturn(List.of(entry("phish", "https://attacker@www.youtube.com/watch?v=abc")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).doesNotContain("href=\"https://attacker@");
        assertThat(body)
                .doesNotContain(
                        "href=\"https://www.youtube.com/watch?v=abc"); // even host-only fallback shouldn't anchor
    }

    @Test
    void emptyList_rendersPlaceholder() throws Exception {
        when(recorder.recent()).thenReturn(List.of());

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("No videos processed yet.");
    }

    @Test
    void tagsRenderedCommaSeparatedAndIndividuallyEscaped() throws Exception {
        when(recorder.recent())
                .thenReturn(List.of(new ProcessedVideoEntry(
                        Instant.parse("2026-05-11T20:14:33Z"),
                        Source.PLAYLIST,
                        "t",
                        "https://www.youtube.com/watch?v=abc",
                        ProcessingStatus.SUCCESS,
                        List.of("safe", "<x>"),
                        "Videos")));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("safe, &lt;x&gt;");
    }

    @Test
    void nullCollection_rendersEmDash() throws Exception {
        when(recorder.recent())
                .thenReturn(List.of(new ProcessedVideoEntry(
                        Instant.parse("2026-05-11T20:14:33Z"),
                        Source.UNSORTED,
                        "t",
                        "https://www.youtube.com/watch?v=abc",
                        ProcessingStatus.FAILED,
                        List.of(),
                        null)));

        String body = body(mockMvc.perform(get("/processed")).andReturn());

        assertThat(body).contains("—");
    }

    private ProcessedVideoEntry entry(String title, String url) {
        return new ProcessedVideoEntry(
                Instant.parse("2026-05-11T20:14:33Z"),
                Source.PLAYLIST,
                title,
                url,
                ProcessingStatus.SUCCESS,
                List.of(),
                "Videos");
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
