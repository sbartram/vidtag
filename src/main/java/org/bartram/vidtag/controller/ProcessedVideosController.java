package org.bartram.vidtag.controller;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bartram.vidtag.model.ProcessedVideoEntry;
import org.bartram.vidtag.service.ProcessedVideoRecorder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders {@code GET /processed} — a server-side HTML table of the last 100
 * processed videos. All dynamic values are HTML-escaped, and the title anchor
 * is only emitted when the entry URL passes a scheme + host allowlist.
 */
@Controller
@RequiredArgsConstructor
public class ProcessedVideosController {

    private static final Set<String> ALLOWED_HOSTS = Set.of("www.youtube.com", "youtu.be", "m.youtube.com");

    private final ProcessedVideoRecorder recorder;

    @GetMapping(value = "/processed", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @ResponseBody
    public String list() {
        return renderPage(recorder.recent());
    }

    private String renderPage(List<ProcessedVideoEntry> entries) {
        StringBuilder body = new StringBuilder();
        body.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<title>Processed videos</title>")
                .append("<style>body{font-family:monospace;margin:1rem;}")
                .append("table{width:100%;border-collapse:collapse;}")
                .append("th,td{border-bottom:1px solid #ddd;padding:.25rem .5rem;text-align:left;vertical-align:top;}")
                .append("th{background:#f4f4f4;}</style></head><body>");
        body.append("<h1>Processed videos (last ").append(entries.size()).append(")</h1>");

        if (entries.isEmpty()) {
            body.append("<p>No videos processed yet.</p>");
        } else {
            body.append("<table><thead><tr>")
                    .append("<th>When</th><th>Source</th><th>Title</th>")
                    .append("<th>Status</th><th>Tags</th><th>Collection</th>")
                    .append("</tr></thead><tbody>");
            for (ProcessedVideoEntry e : entries) {
                body.append("<tr>")
                        .append("<td>")
                        .append(esc(DateTimeFormatter.ISO_INSTANT.format(e.timestamp())))
                        .append("</td>")
                        .append("<td>")
                        .append(esc(e.source().name().toLowerCase()))
                        .append("</td>")
                        .append("<td>")
                        .append(renderTitle(e.title(), e.url()))
                        .append("</td>")
                        .append("<td>")
                        .append(esc(e.status().name().toLowerCase()))
                        .append("</td>")
                        .append("<td>")
                        .append(renderTags(e.tags()))
                        .append("</td>")
                        .append("<td>")
                        .append(e.collection() == null ? "—" : esc(e.collection()))
                        .append("</td>")
                        .append("</tr>");
            }
            body.append("</tbody></table>");
        }

        body.append("</body></html>");
        return body.toString();
    }

    private String renderTitle(String title, String url) {
        String safeTitle = esc(title);
        if (isAllowedUrl(url)) {
            return "<a href=\"" + esc(url) + "\">" + safeTitle + "</a>";
        }
        return safeTitle;
    }

    private String renderTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(esc(tags.get(i)));
        }
        return out.toString();
    }

    private boolean isAllowedUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getUserInfo() == null
                    && ALLOWED_HOSTS.contains(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
