package com.factcheck.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private String adminName = "admin";
    private String adminPassword = "admin";
    private String jwtSecret = "change_me_please_use_32_chars_min";
    private String jwtIssuer = "factcheck-backend";
    private long jwtTtlMinutes = 120;
    private String jwtCookieName = "factcheck_token";
    private boolean jwtCookieSecure = false;
    private String jwtCookieSameSite = "Strict";

}
