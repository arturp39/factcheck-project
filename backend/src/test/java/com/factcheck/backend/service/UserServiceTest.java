package com.factcheck.backend.service;

import com.factcheck.backend.entity.AppUser;
import com.factcheck.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registerUser_rejectsBlankUsername() {
        assertThrows(IllegalArgumentException.class, () -> userService.registerUser(" ", "password123"));
    }

    @Test
    void registerUser_rejectsNullPassword() {
        assertThrows(IllegalArgumentException.class, () -> userService.registerUser("user", null));
    }

    @Test
    void registerUser_rejectsShortPassword() {
        assertThrows(IllegalArgumentException.class, () -> userService.registerUser("user", "short"));
    }

    @Test
    void registerUser_rejectsDuplicateUsernames() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.registerUser("Alice", "password123"));
    }

    @Test
    void registerUser_persistsNormalizedUser() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser created = userService.registerUser(" Alice ", "password123");

        assertThat(created.getUsername()).isEqualTo("alice");
        assertThat(created.getPasswordHash()).isEqualTo("hash");
        assertThat(created.getRole()).isEqualTo("USER");
    }

    @Test
    void findByUsername_normalizesLookup() {
        AppUser user = new AppUser();
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        Optional<AppUser> result = userService.findByUsername(" Alice ");

        assertThat(result).containsSame(user);
        verify(userRepository).findByUsername("alice");
    }

    @Test
    void ensureAdminUser_rejectsBlankCredentials() {
        assertThrows(IllegalArgumentException.class, () -> userService.ensureAdminUser(" ", " "));
    }

    @Test
    void ensureAdminUser_returnsExistingAdmin() {
        AppUser existing = new AppUser();
        existing.setUsername("admin");
        existing.setRole("ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        AppUser result = userService.ensureAdminUser("admin", "password123");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void ensureAdminUser_createsWhenMissing() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AppUser result = userService.ensureAdminUser("Admin", "password123");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(result.getUsername()).isEqualTo("admin");
    }
}
