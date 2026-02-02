package org.bartram.vidtag.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.List;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RaindropApiClientImplTest {

    private static final String BASE_URL = "https://api.raindrop.io/rest/v1";
    private static final String API_TOKEN = "test-token";

    private MockRestServiceServer mockServer;
    private RaindropApiClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + API_TOKEN)
                .defaultHeader("Content-Type", "application/json");

        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new RaindropApiClientImpl(builder, API_TOKEN);
    }

    @Nested
    class GetUserTags {

        @Test
        void shouldReturnTagsSuccessfully() {
            // Given
            String responseJson = """
                    {
                        "items": [
                            {"_id": "java", "count": 10},
                            {"_id": "spring", "count": 5},
                            {"_id": "tutorial", "count": 3}
                        ]
                    }
                    """;

            mockServer
                    .expect(requestTo(BASE_URL + "/tags"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer " + API_TOKEN))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When
            List<RaindropTag> tags = client.getUserTags("user123");

            // Then
            assertThat(tags).hasSize(3);
            assertThat(tags).extracting(RaindropTag::name).containsExactly("java", "spring", "tutorial");
            mockServer.verify();
        }

        @Test
        void shouldReturnEmptyListWhenNoTags() {
            // Given
            String responseJson = """
                    {"items": null}
                    """;

            mockServer
                    .expect(requestTo(BASE_URL + "/tags"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When
            List<RaindropTag> tags = client.getUserTags("user123");

            // Then
            assertThat(tags).isEmpty();
            mockServer.verify();
        }

        @Test
        void shouldReturnEmptyListWhenNullResponse() {
            // Given
            mockServer
                    .expect(requestTo(BASE_URL + "/tags"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

            // When
            List<RaindropTag> tags = client.getUserTags("user123");

            // Then
            assertThat(tags).isEmpty();
            mockServer.verify();
        }

        @Test
        void shouldThrowExceptionOnApiError() {
            // Given
            mockServer
                    .expect(requestTo(BASE_URL + "/tags"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError());

            // When/Then
            assertThatThrownBy(() -> client.getUserTags("user123"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to fetch Raindrop tags");
            mockServer.verify();
        }
    }

    @Nested
    class GetUserCollections {

        @Test
        void shouldReturnCollectionsSuccessfully() {
            // Given
            String responseJson = """
                    {
                        "items": [
                            {"_id": 1, "title": "Videos"},
                            {"_id": 2, "title": "Articles"},
                            {"_id": 3, "title": "Tutorials"}
                        ]
                    }
                    """;

            mockServer
                    .expect(requestTo(BASE_URL + "/collections"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When
            List<RaindropCollection> collections = client.getUserCollections("user123");

            // Then
            assertThat(collections).hasSize(3);
            assertThat(collections.get(0).id()).isEqualTo(1L);
            assertThat(collections.get(0).title()).isEqualTo("Videos");
            mockServer.verify();
        }

        @Test
        void shouldReturnEmptyListWhenNoCollections() {
            // Given
            String responseJson = """
                    {"items": null}
                    """;

            mockServer
                    .expect(requestTo(BASE_URL + "/collections"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When
            List<RaindropCollection> collections = client.getUserCollections("user123");

            // Then
            assertThat(collections).isEmpty();
            mockServer.verify();
        }

        @Test
        void shouldThrowExceptionOnApiError() {
            // Given
            mockServer
                    .expect(requestTo(BASE_URL + "/collections"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError());

            // When/Then
            assertThatThrownBy(() -> client.getUserCollections("user123"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to fetch Raindrop collections");
            mockServer.verify();
        }
    }

    @Nested
    class BookmarkExists {

        @Test
        void shouldReturnTrueWhenBookmarkExists() {
            // Given
            String responseJson = """
                    {
                        "items": [
                            {"_id": 123, "link": "https://youtube.com/watch?v=abc", "title": "Test"}
                        ]
                    }
                    """;

            mockServer
                    .expect(requestToUriTemplate(
                            BASE_URL + "/raindrops/{collectionId}?search={url}&perpage={perpage}",
                            "123",
                            "https://youtube.com/watch?v=abc",
                            "1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When
            boolean exists = client.bookmarkExists(123L, "https://youtube.com/watch?v=abc");

            // Then
            assertThat(exists).isTrue();
            mockServer.verify();
        }

        @Test
        void shouldReturnFalseWhenBookmarkDoesNotExist() {
            // Given
            String responseJson = """
                    {"items": []}
                    """;

            mockServer
                    .expect(requestToUriTemplate(
                            BASE_URL + "/raindrops/{collectionId}?search={url}&perpage={perpage}",
                            "123",
                            "https://youtube.com/watch?v=xyz",
                            "1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When
            boolean exists = client.bookmarkExists(123L, "https://youtube.com/watch?v=xyz");

            // Then
            assertThat(exists).isFalse();
            mockServer.verify();
        }

        @Test
        void shouldReturnFalseOnApiError() {
            // Given
            mockServer
                    .expect(requestToUriTemplate(
                            BASE_URL + "/raindrops/{collectionId}?search={url}&perpage={perpage}",
                            "123",
                            "https://youtube.com/watch?v=error",
                            "1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError());

            // When
            boolean exists = client.bookmarkExists(123L, "https://youtube.com/watch?v=error");

            // Then - Should return false on error (fail open)
            assertThat(exists).isFalse();
            mockServer.verify();
        }
    }

    @Nested
    class CreateBookmark {

        @Test
        void shouldCreateBookmarkSuccessfully() {
            // Given
            mockServer
                    .expect(requestTo(BASE_URL + "/raindrop"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(content().json("""
                            {
                                "link": "https://youtube.com/watch?v=abc",
                                "title": "Test Video",
                                "collection": {"$id": 123},
                                "tags": ["java", "tutorial"]
                            }
                            """))
                    .andRespond(withSuccess());

            // When/Then - No exception thrown
            client.createBookmark(123L, "https://youtube.com/watch?v=abc", "Test Video", List.of("java", "tutorial"));
            mockServer.verify();
        }

        @Test
        void shouldThrowExceptionOnApiError() {
            // Given
            mockServer
                    .expect(requestTo(BASE_URL + "/raindrop"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withServerError());

            // When/Then
            assertThatThrownBy(() -> client.createBookmark(123L, "https://example.com", "Test", List.of("tag")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create Raindrop bookmark");
            mockServer.verify();
        }
    }

    @Nested
    class GetCollections {

        @Test
        void shouldReturnCollectionsSuccessfully() {
            // Given
            String responseJson = """
                    {
                        "items": [
                            {"_id": 1, "title": "Videos"},
                            {"_id": 2, "title": "Articles"}
                        ]
                    }
                    """;

            mockServer
                    .expect(requestTo(BASE_URL + "/collections"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When
            List<RaindropCollection> collections = client.getCollections();

            // Then
            assertThat(collections).hasSize(2);
            assertThat(collections).extracting(RaindropCollection::title).containsExactly("Videos", "Articles");
            mockServer.verify();
        }

        @Test
        void shouldReturnEmptyListOnError() {
            // Given
            mockServer
                    .expect(requestTo(BASE_URL + "/collections"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError());

            // When
            List<RaindropCollection> collections = client.getCollections();

            // Then - Should return empty list on error (not throw)
            assertThat(collections).isEmpty();
            mockServer.verify();
        }

        @Test
        void shouldReturnEmptyListWhenNullItems() {
            // Given
            String responseJson = """
                    {"items": null}
                    """;

            mockServer
                    .expect(requestTo(BASE_URL + "/collections"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When
            List<RaindropCollection> collections = client.getCollections();

            // Then
            assertThat(collections).isEmpty();
            mockServer.verify();
        }
    }

    @Nested
    class CreateCollection {

        @Test
        void shouldCreateCollectionAndReturnId() {
            // Given
            String responseJson = """
                    {
                        "item": {"_id": 42, "title": "New Collection"}
                    }
                    """;

            mockServer
                    .expect(requestTo(BASE_URL + "/collection"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(content().json("""
                            {"title": "New Collection"}
                            """))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When
            Long collectionId = client.createCollection("New Collection");

            // Then
            assertThat(collectionId).isEqualTo(42L);
            mockServer.verify();
        }

        @Test
        void shouldThrowExceptionWhenResponseIsNull() {
            // Given
            mockServer
                    .expect(requestTo(BASE_URL + "/collection"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

            // When/Then
            assertThatThrownBy(() -> client.createCollection("Test"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create collection");
            mockServer.verify();
        }

        @Test
        void shouldThrowExceptionWhenItemIsNull() {
            // Given
            String responseJson = """
                    {"item": null}
                    """;

            mockServer
                    .expect(requestTo(BASE_URL + "/collection"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

            // When/Then
            assertThatThrownBy(() -> client.createCollection("Test"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create collection");
            mockServer.verify();
        }

        @Test
        void shouldThrowExceptionOnApiError() {
            // Given
            mockServer
                    .expect(requestTo(BASE_URL + "/collection"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withServerError());

            // When/Then
            assertThatThrownBy(() -> client.createCollection("Test"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create collection");
            mockServer.verify();
        }
    }
}
