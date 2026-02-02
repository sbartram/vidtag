package org.bartram.vidtag.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Accessors(fluent = false)
@Component
@ConfigurationProperties(prefix = "vidtag.tagging")
public class TagFilterProperties {

    private String blockedTags = "";

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
