package org.bartram.vidtag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "vidtag.tagging")
public class TagFilterProperties {

    private String blockedTags = "";

    public String getBlockedTags() {
        return blockedTags;
    }

    public void setBlockedTags(String blockedTags) {
        this.blockedTags = blockedTags;
    }

    public Set<String> getBlockedTagsSet() {
        if (blockedTags == null || blockedTags.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(blockedTags.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(tag -> !tag.isEmpty())
            .collect(Collectors.toSet());
    }

    public boolean isFilterEnabled() {
        return !getBlockedTagsSet().isEmpty();
    }
}
