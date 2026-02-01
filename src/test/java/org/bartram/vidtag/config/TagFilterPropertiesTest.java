package org.bartram.vidtag.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TagFilterPropertiesTest {

    @Test
    void shouldReturnEmptySetWhenBlockedTagsIsEmpty() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags("");

        Set<String> result = properties.getBlockedTagsSet();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldParseSingleTag() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags("spam");

        Set<String> result = properties.getBlockedTagsSet();

        assertThat(result).containsExactly("spam");
    }

    @Test
    void shouldParseMultipleTags() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags("spam,promotional,clickbait");

        Set<String> result = properties.getBlockedTagsSet();

        assertThat(result).containsExactlyInAnyOrder("spam", "promotional", "clickbait");
    }

    @Test
    void shouldNormalizeToLowerCase() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags("SPAM,Promotional,ClickBait");

        Set<String> result = properties.getBlockedTagsSet();

        assertThat(result).containsExactlyInAnyOrder("spam", "promotional", "clickbait");
    }

    @Test
    void shouldTrimWhitespace() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags("  spam  ,  promotional  ,  clickbait  ");

        Set<String> result = properties.getBlockedTagsSet();

        assertThat(result).containsExactlyInAnyOrder("spam", "promotional", "clickbait");
    }

    @Test
    void shouldFilterEmptyValues() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags("spam,,promotional,  ,clickbait");

        Set<String> result = properties.getBlockedTagsSet();

        assertThat(result).containsExactlyInAnyOrder("spam", "promotional", "clickbait");
    }

    @Test
    void shouldReturnTrueWhenFilterEnabled() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags("spam,promotional");

        boolean result = properties.isFilterEnabled();

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenFilterDisabled() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags("");

        boolean result = properties.isFilterEnabled();

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenBlockedTagsIsNull() {
        TagFilterProperties properties = new TagFilterProperties();
        properties.setBlockedTags(null);

        boolean result = properties.isFilterEnabled();

        assertThat(result).isFalse();
    }
}
