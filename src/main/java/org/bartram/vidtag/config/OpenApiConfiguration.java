package org.bartram.vidtag.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 * Access Swagger UI at: http://localhost:8080/swagger-ui.html
 * Access OpenAPI JSON at: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI vidtagOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("VidTag API")
                .description("""
                    AI-powered video tagging service that analyzes YouTube playlist videos
                    and automatically creates bookmarks in Raindrop.io with intelligent tags.

                    ## Features
                    - Fetch videos from YouTube playlists
                    - AI-powered tag generation using Claude
                    - Smart tag selection from existing Raindrop tags
                    - Real-time progress via Server-Sent Events (SSE)
                    - Circuit breakers and retry logic for external APIs
                    - Redis caching for improved performance

                    ## External APIs Used
                    - **YouTube Data API v3** - Video metadata retrieval
                    - **Raindrop.io API** - Bookmark and tag management
                    - **Anthropic Claude API** - AI-powered video analysis and tagging
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("VidTag")
                    .url("https://github.com/yourusername/vidtag"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Development server")
            ));
    }
}
