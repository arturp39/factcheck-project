package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "vertex")
public class VertexProperties {

    private String projectId;
    private String location;
    private String modelName;
    private String credentialsPath;
}