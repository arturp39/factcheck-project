    package com.example.demo.config;

    import lombok.Data;
    import org.springframework.boot.context.properties.ConfigurationProperties;
    import org.springframework.stereotype.Component;

    @Data
    @Component
    @ConfigurationProperties(prefix = "weaviate")
    public class WeaviateProperties {
        private String baseUrl;
        private String apiKey;
        private float maxDistance = 0.5f;
    }