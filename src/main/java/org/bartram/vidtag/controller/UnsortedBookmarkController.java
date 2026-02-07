package org.bartram.vidtag.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.service.UnsortedBookmarkProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/unsorted")
@RequiredArgsConstructor
@ConditionalOnBean(UnsortedBookmarkProcessor.class)
public class UnsortedBookmarkController {

    private final UnsortedBookmarkProcessor unsortedBookmarkProcessor;

    @PostMapping("/process")
    public ResponseEntity<String> processUnsortedBookmarks() {
        log.atInfo().setMessage("Manual trigger: processing unsorted bookmarks").log();
        unsortedBookmarkProcessor.processUnsortedBookmarks();
        return ResponseEntity.ok("Unsorted bookmark processing completed");
    }
}
