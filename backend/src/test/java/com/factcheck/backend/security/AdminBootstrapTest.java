package com.factcheck.backend.security;

import com.factcheck.backend.config.SecurityProperties;
import com.factcheck.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserService userService;

    @Test
    void run_invokesEnsureAdminUser() {
        SecurityProperties props = new SecurityProperties();
        props.setAdminName("admin");
        props.setAdminPassword("secret");

        AdminBootstrap bootstrap = new AdminBootstrap(userService, props);
        bootstrap.run();

        verify(userService).ensureAdminUser("admin", "secret");
    }

    @Test
    void run_swallowInvalidAdminConfig() {
        SecurityProperties props = new SecurityProperties();
        props.setAdminName(" ");
        props.setAdminPassword(" ");
        AdminBootstrap bootstrap = new AdminBootstrap(userService, props);

        doThrow(new IllegalArgumentException("bad")).when(userService).ensureAdminUser(" ", " ");

        bootstrap.run();
    }
}
