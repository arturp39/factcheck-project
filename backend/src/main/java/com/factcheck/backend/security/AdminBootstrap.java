package com.factcheck.backend.security;

import com.factcheck.backend.config.SecurityProperties;
import com.factcheck.backend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminBootstrap implements CommandLineRunner {

    private final UserService userService;
    private final SecurityProperties securityProperties;

    public AdminBootstrap(UserService userService, SecurityProperties securityProperties) {
        this.userService = userService;
        this.securityProperties = securityProperties;
    }

    @Override
    public void run(String... args) {
        try {
            userService.ensureAdminUser(
                    securityProperties.getAdminName(),
                    securityProperties.getAdminPassword()
            );
        } catch (IllegalArgumentException e) {
            log.warn("Admin bootstrap skipped: {}", e.getMessage());
        }
    }
}
