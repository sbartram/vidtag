package org.bartram.vidtag.client.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.bartram.vidtag.client.RaindropApiClient;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;
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
@Slf4j
@Component
@ConditionalOnProperty(name = "raindrop.api.token")
public class RaindropApiClientImpl implements RaindropApiClient {

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
        log.atDebug().setMessage("Fetching tags for user: {}").addArgument(userId).log();

        try {
            TagsResponse response = restClient.get()
                    .uri("/tags")
                    .retrieve()
                    .body(TagsResponse.class);

            if (response == null || response.items == null) {
                log.atWarn().setMessage("No tags found for user: {}").addArgument(userId).log();
                return List.of();
            }

            List<RaindropTag> tags = response.items.stream()
                    .map(tagItem -> new RaindropTag(tagItem.tag))
                    .collect(Collectors.toList());

            log.atInfo().setMessage("Fetched {} tags for user {}").addArgument(tags.size()).addArgument(userId).log();
            return tags;

        } catch (Exception e) {
            log.atError().setMessage("Failed to fetch tags for user: {}").addArgument(userId).setCause(e).log();
            throw new RuntimeException("Failed to fetch Raindrop tags", e);
        }
    }

    @Override
    public List<RaindropCollection> getUserCollections(String userId) {
        log.atDebug().setMessage("Fetching collections for user: {}").addArgument(userId).log();

        try {
            CollectionsResponse response = restClient.get()
                    .uri("/collections")
                    .retrieve()
                    .body(CollectionsResponse.class);

            if (response == null || response.items == null) {
                log.atWarn().setMessage("No collections found for user: {}").addArgument(userId).log();
                return List.of();
            }

            List<RaindropCollection> collections = response.items.stream()
                    .map(item -> new RaindropCollection(item.id, item.title))
                    .collect(Collectors.toList());

            log.atInfo().setMessage("Fetched {} collections for user {}").addArgument(collections.size()).addArgument(userId).log();
            return collections;

        } catch (Exception e) {
            log.atError().setMessage("Failed to fetch collections for user: {}").addArgument(userId).setCause(e).log();
            throw new RuntimeException("Failed to fetch Raindrop collections", e);
        }
    }

    @Override
    public boolean bookmarkExists(Long collectionId, String url) {
        log.atDebug().setMessage("Checking if bookmark exists in collection {}: {}").addArgument(collectionId).addArgument(url).log();

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

            log.atDebug().setMessage("Bookmark exists in collection {}: {}").addArgument(collectionId).addArgument(exists).log();
            return exists;

        } catch (Exception e) {
            log.atError().setMessage("Failed to check bookmark existence: {}").addArgument(url).setCause(e).log();
            return false; // Assume doesn't exist on error
        }
    }

    @Override
    public void createBookmark(Long collectionId, String url, String title, List<String> tags) {
        log.atDebug().setMessage("Creating bookmark in collection {}: {}").addArgument(collectionId).addArgument(url).log();

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

            log.atInfo().setMessage("Created bookmark in collection {}: {}").addArgument(collectionId).addArgument(title).log();

        } catch (Exception e) {
            log.atError().setMessage("Failed to create bookmark: {}").addArgument(url).setCause(e).log();
            throw new RuntimeException("Failed to create Raindrop bookmark", e);
        }
    }

    @Override
    public List<RaindropCollection> getCollections() {
        log.atDebug().setMessage("Fetching all collections").log();

        try {
            CollectionsResponse response = restClient.get()
                .uri("/collections")
                .retrieve()
                .body(CollectionsResponse.class);

            if (response == null || response.items == null) {
                log.atWarn().setMessage("No collections found").log();
                return List.of();
            }

            List<RaindropCollection> collections = response.items.stream()
                .map(item -> new RaindropCollection(item.id, item.title))
                .collect(Collectors.toList());

            log.atDebug().setMessage("Retrieved {} collections").addArgument(collections.size()).log();
            return collections;

        } catch (Exception e) {
            log.atError().setMessage("Error fetching collections: {}").addArgument(e.getMessage()).setCause(e).log();
            return List.of();
        }
    }

    @Override
    public Long createCollection(String title) {
        log.atDebug().setMessage("Creating collection: {}").addArgument(title).log();

        try {
            Map<String, Object> requestBody = Map.of("title", title);

            CollectionCreateResponse response = restClient.post()
                .uri("/collection")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(CollectionCreateResponse.class);

            if (response == null || response.item == null) {
                log.atError().setMessage("Failed to create collection '{}': null response").addArgument(title).log();
                throw new RuntimeException("Failed to create collection: " + title);
            }

            Long collectionId = response.item.id;
            log.atInfo().setMessage("Created collection '{}' with ID: {}").addArgument(title).addArgument(collectionId).log();
            return collectionId;

        } catch (Exception e) {
            log.atError().setMessage("Error creating collection '{}': {}").addArgument(title).addArgument(e.getMessage()).setCause(e).log();
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
