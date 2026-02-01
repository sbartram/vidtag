package org.bartram.vidtag;

import org.bartram.vidtag.client.RaindropApiClient;
import org.bartram.vidtag.client.YouTubeApiClient;
import org.bartram.vidtag.model.RaindropCollection;
import org.bartram.vidtag.model.RaindropTag;
import org.bartram.vidtag.model.VideoMetadata;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
	}

	/**
	 * Provides a stub YouTubeApiClient for tests when API key is not configured.
	 * The real implementation (YouTubeApiClientImpl) will be used when API key is provided.
	 */
	@Bean
	@ConditionalOnMissingBean(YouTubeApiClient.class)
	YouTubeApiClient youtubeApiClient() {
		return new YouTubeApiClient() {
			@Override
			public List<VideoMetadata> getPlaylistVideos(String playlistId) {
				return Collections.emptyList();
			}

			@Override
			public String findPlaylistByName(String playlistName) {
				return null;
			}
		};
	}

	/**
	 * Provides a stub RaindropApiClient for tests when API token is not configured.
	 * The real implementation (RaindropApiClientImpl) will be used when API token is provided.
	 */
	@Bean
	@ConditionalOnMissingBean(RaindropApiClient.class)
	RaindropApiClient raindropApiClient() {
		return new RaindropApiClient() {
			@Override
			public List<RaindropTag> getUserTags(String userId) {
				return Collections.emptyList();
			}

			@Override
			public List<RaindropCollection> getUserCollections(String userId) {
				return Collections.emptyList();
			}

			@Override
			public boolean bookmarkExists(Long collectionId, String url) {
				return false;
			}

			@Override
			public void createBookmark(Long collectionId, String url, String title, List<String> tags) {
				// Stub - no-op
			}

			@Override
			public List<RaindropCollection> getCollections() {
				return Collections.emptyList();
			}

			@Override
			public Long createCollection(String title) {
				return 1L; // Return dummy ID
			}
		};
	}

	/**
	 * Provides a mock ChatClient for tests when Claude API key is not configured.
	 */
	@Bean
	@ConditionalOnMissingBean(ChatClient.class)
	ChatClient chatClient() {
		ChatClient chatClient = mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

		when(chatClient.prompt(anyString())).thenReturn(requestSpec);
		when(requestSpec.call()).thenReturn(callResponseSpec);
		when(callResponseSpec.content()).thenReturn("Videos"); // Default fallback collection

		return chatClient;
	}

}
