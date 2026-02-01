package org.bartram.vidtag.client.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bartram.vidtag.client.RaindropApiClient;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of RaindropApiClient using Raindrop.io REST API.
 * Only activated when raindrop.api.token property is set.
 */
@Component
@ConditionalOnProperty(name = "raindrop.api.token")
public class RaindropApiClientImpl implements RaindropApiClient {

    private static final Logger log = LoggerFactory.getLogger(RaindropApiClientImpl.class);
    private static final String BASE_URL = "https://api.raindrop.io/rest/v1";

    private final RestClient restClient;

    public RaindropApiClientImpl(@Value("${raindrop.api.token}") String apiToken) {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + apiToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public List<RaindropTag> getUserTags(String userId) {
        log.debug("Fetching tags for user: {}", userId);

        try {
            TagsResponse response = restClient.get()
                    .uri("/tags")
                    .retrieve()
                    .body(TagsResponse.class);

            if (response == null || response.items == null) {
                log.warn("No tags found for user: {}", userId);
                return List.of();
            }

            List<RaindropTag> tags = response.items.stream()
                    .map(tagItem -> new RaindropTag(tagItem.tag))
                    .collect(Collectors.toList());

            log.info("Fetched {} tags for user {}", tags.size(), userId);
            return tags;

        } catch (Exception e) {
            log.error("Failed to fetch tags for user: {}", userId, e);
            throw new RuntimeException("Failed to fetch Raindrop tags", e);
        }
    }

    @Override
    public List<RaindropCollection> getUserCollections(String userId) {
        log.debug("Fetching collections for user: {}", userId);

        try {
            CollectionsResponse response = restClient.get()
                    .uri("/collections")
                    .retrieve()
                    .body(CollectionsResponse.class);

            if (response == null || response.items == null) {
                log.warn("No collections found for user: {}", userId);
                return List.of();
            }

            List<RaindropCollection> collections = response.items.stream()
                    .map(item -> new RaindropCollection(item.id, item.title))
                    .collect(Collectors.toList());

            log.info("Fetched {} collections for user {}", collections.size(), userId);
            return collections;

        } catch (Exception e) {
            log.error("Failed to fetch collections for user: {}", userId, e);
            throw new RuntimeException("Failed to fetch Raindrop collections", e);
        }
    }

    @Override
    public boolean bookmarkExists(Long collectionId, String url) {
        log.debug("Checking if bookmark exists in collection {}: {}", collectionId, url);

        try {
            // Search for raindrops in the collection by URL
            RaindropsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/raindrops/" + collectionId)
                            .queryParam("search", url)
                            .queryParam("perpage", "1")
                            .build())
                    .retrieve()
                    .body(RaindropsResponse.class);

            boolean exists = response != null &&
                            response.items != null &&
                            !response.items.isEmpty();

            log.debug("Bookmark exists in collection {}: {}", collectionId, exists);
            return exists;

        } catch (Exception e) {
            log.error("Failed to check bookmark existence: {}", url, e);
            return false; // Assume doesn't exist on error
        }
    }

    @Override
    public void createBookmark(Long collectionId, String url, String title, List<String> tags) {
        log.debug("Creating bookmark in collection {}: {}", collectionId, url);

        try {
            CreateRaindropRequest request = new CreateRaindropRequest(
                    url,
                    title,
                    collectionId,
                    tags
            );

            restClient.post()
                    .uri("/raindrop")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Created bookmark in collection {}: {}", collectionId, title);

        } catch (Exception e) {
            log.error("Failed to create bookmark: {}", url, e);
            throw new RuntimeException("Failed to create Raindrop bookmark", e);
        }
    }

    @Override
    public List<RaindropCollection> getCollections() {
        log.debug("Fetching all collections");

        try {
            CollectionsResponse response = restClient.get()
                .uri("/collections")
                .retrieve()
                .body(CollectionsResponse.class);

            if (response == null || response.items == null) {
                log.warn("No collections found");
                return List.of();
            }

            List<RaindropCollection> collections = response.items.stream()
                .map(item -> new RaindropCollection(item.id, item.title))
                .collect(Collectors.toList());

            log.debug("Retrieved {} collections", collections.size());
            return collections;

        } catch (Exception e) {
            log.error("Error fetching collections: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public Long createCollection(String title) {
        log.debug("Creating collection: {}", title);

        try {
            Map<String, Object> requestBody = Map.of("title", title);

            CollectionCreateResponse response = restClient.post()
                .uri("/collection")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(CollectionCreateResponse.class);

            if (response == null || response.item == null) {
                log.error("Failed to create collection '{}': null response", title);
                throw new RuntimeException("Failed to create collection: " + title);
            }

            Long collectionId = response.item.id;
            log.info("Created collection '{}' with ID: {}", title, collectionId);
            return collectionId;

        } catch (Exception e) {
            log.error("Error creating collection '{}': {}", title, e.getMessage(), e);
            throw new RuntimeException("Failed to create collection: " + title, e);
        }
    }

    // Response DTOs for Raindrop API

    private record TagsResponse(List<TagItem> items) {
    }

    private record TagItem(
            @JsonProperty("_id") String tag,
            int count
    ) {
    }

    private record CollectionsResponse(List<CollectionItem> items) {
    }

    private record CollectionItem(
            @JsonProperty("_id") Long id,
            String title
    ) {
    }

    private record RaindropsResponse(List<RaindropItem> items) {
    }

    private record RaindropItem(
            @JsonProperty("_id") Long id,
            String link,
            String title
    ) {
    }

    private record CreateRaindropRequest(
            String link,
            String title,
            Collection collection,
            List<String> tags
    ) {
        CreateRaindropRequest(String link, String title, Long collectionId, List<String> tags) {
            this(link, title, new Collection(collectionId), tags);
        }
    }

    private record Collection(
            @JsonProperty("$id") Long id
    ) {
    }

    private record CollectionCreateResponse(CollectionItem item) {
    }
}
