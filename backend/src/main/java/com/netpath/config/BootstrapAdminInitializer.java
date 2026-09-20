package com.netpath.config;

import com.netpath.entity.User;
import com.netpath.entity.UserRole;
import com.netpath.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the very first operator when the database has no users at all.
 *
 * <p>Runs after {@link DemoDataInitializer} so that a demo estate seeded on a development machine
 * keeps its own operator instead of producing a second one. Creating an account is the entire job:
 * no applications, no paths, no telemetry.
 */
@Component
@Order(2)
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    private static final int MINIMUM_PASSWORD_LENGTH = 8;

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(BootstrapAdminProperties properties,
                                     UserRepository userRepository,
                                     PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        // Misconfiguration must fail loudly: silently skipping would leave an operator locked out of
        // a deployment they believe they just provisioned.
        if (properties.getEmail().isBlank() || properties.getPassword().isBlank()) {
            throw new IllegalStateException(
                    "app.bootstrap-admin is enabled but BOOTSTRAP_ADMIN_EMAIL or "
                            + "BOOTSTRAP_ADMIN_PASSWORD is empty.");
        }
        if (properties.getPassword().length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must be at least " + MINIMUM_PASSWORD_LENGTH
                            + " characters.");
        }

        if (userRepository.count() > 0) {
            logger.info("Bootstrap admin skipped: the database already has operators.");
            return;
        }

        userRepository.save(new User(
                properties.getEmail(),
                passwordEncoder.encode(properties.getPassword()),
                properties.getName(),
                UserRole.ADMIN));

        logger.info("Created the first operator {}. Disable app.bootstrap-admin now that it exists.",
                properties.getEmail());
    }
}
