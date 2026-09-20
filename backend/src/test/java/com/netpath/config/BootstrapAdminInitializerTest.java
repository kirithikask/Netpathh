package com.netpath.config;

import com.netpath.entity.User;
import com.netpath.entity.UserRole;
import com.netpath.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provisioning the first operator is the one thing that makes a freshly migrated production database
 * usable, so the guard rails matter more than the happy path.
 */
class BootstrapAdminInitializerTest {

    private BootstrapAdminProperties properties;
    private UserRepository userRepository;
    private BootstrapAdminInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new BootstrapAdminProperties();
        properties.setEnabled(true);
        properties.setEmail("operator@netpath.io");
        properties.setPassword("s3cure-enough");

        userRepository = mock(UserRepository.class);

        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        initializer = new BootstrapAdminInitializer(properties, userRepository, passwordEncoder);
    }

    @Test
    void createsTheFirstOperatorOnAnEmptyDatabase() {
        when(userRepository.count()).thenReturn(0L);

        initializer.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertThat(saved.getValue().getEmail()).isEqualTo("operator@netpath.io");
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void doesNothingWhenItIsNotEnabled() {
        properties.setEnabled(false);

        initializer.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void neverTouchesADatabaseThatAlreadyHasUsers() {
        when(userRepository.count()).thenReturn(3L);

        initializer.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void refusesToStartWhenEnabledWithoutCredentials() {
        properties.setPassword("");

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void refusesAShortPassword() {
        properties.setPassword("short");

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least");
    }
}
