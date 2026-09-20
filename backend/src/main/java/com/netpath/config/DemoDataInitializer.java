package com.netpath.config;

import com.netpath.entity.*;
import com.netpath.repository.*;
import com.netpath.service.PathHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

/**
 * Seeds a small but realistic network so the platform is demonstrable on first run.
 *
 * <p>Only runs when the database has no users, so it never overwrites real data. Disable with
 * {@code app.demo-data.enabled=false}.
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final DemoDataProperties properties;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final EndpointRepository endpointRepository;
    private final NetworkPathRepository networkPathRepository;
    private final PathMetricRepository pathMetricRepository;
    private final PathHealthService pathHealthService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(DemoDataProperties properties,
                               UserRepository userRepository,
                               ApplicationRepository applicationRepository,
                               EndpointRepository endpointRepository,
                               NetworkPathRepository networkPathRepository,
                               PathMetricRepository pathMetricRepository,
                               PathHealthService pathHealthService,
                               PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.endpointRepository = endpointRepository;
        this.networkPathRepository = networkPathRepository;
        this.pathMetricRepository = pathMetricRepository;
        this.pathHealthService = pathHealthService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        if (userRepository.count() > 0) {
            logger.debug("Skipping demo data seed: users already exist");
            return;
        }

        logger.info("Seeding NETPATH demo network (disable with app.demo-data.enabled=false)");

        User operator = userRepository.save(new User(
                properties.getEmail(),
                passwordEncoder.encode(properties.getPassword()),
                "NETPATH Operator",
                UserRole.ADMIN
        ));

        seedPaymentsPlatform(operator);
        seedStreamingPlatform(operator);

        logger.info("Demo data ready. Login with {} / {}", properties.getEmail(), properties.getPassword());
    }

    private void seedPaymentsPlatform(User owner) {
        Application app = applicationRepository.save(new Application(
                "Payments Platform",
                "Card authorization and settlement traffic across three regions",
                owner));

        Endpoint euWest = endpointRepository.save(
                new Endpoint(app, "pay-eu-west-1a", "10.10.1.11", "eu-west-1"));
        Endpoint usEast = endpointRepository.save(
                new Endpoint(app, "pay-us-east-1a", "10.20.1.11", "us-east-1"));
        Endpoint apSouth = endpointRepository.save(
                new Endpoint(app, "pay-ap-south-1a", "10.30.1.11", "ap-south-1"));
        Endpoint euCoreRouter = endpointRepository.save(
                new Endpoint(app, "core-router-eu-1", "10.10.9.1", "eu-west-1"));

        // eu-west -> us-east: one healthy primary, one degraded standby, one better alternative
        NetworkPath euPrimary = networkPathRepository.save(new NetworkPath(
                app, euWest, usEast, "eu-west-to-us-east-primary", 2, "Direct fiber backbone"));
        seedMetrics(euPrimary, 42, 0.05, 940, 18);

        NetworkPath euStandby = networkPathRepository.save(
                trafficPath(app, euWest, usEast, "eu-west-to-us-east-standby", 3, false,
                        "Legacy MPLS standby route"));
        seedMetrics(euStandby, 268, 4.20, 310, 18);

        NetworkPath euViaRouter = networkPathRepository.save(
                trafficPath(app, euWest, usEast, "eu-west-to-us-east-via-core", 4, false,
                        "Alternative route through core-router-eu-1"));
        seedMetrics(euViaRouter, 63, 0.12, 770, 18);

        // eu-west -> ap-south: degraded primary (demo subject) with a healthy alternative
        NetworkPath apPrimary = networkPathRepository.save(new NetworkPath(
                app, euWest, apSouth, "eu-west-to-ap-south-primary", 3, "Trans-Asia backbone"));
        seedMetrics(apPrimary, 252, 2.60, 420, 18);

        NetworkPath apAlternative = networkPathRepository.save(
                trafficPath(app, euWest, apSouth, "eu-west-to-ap-south-alternative", 5, false,
                        "Reroute via core-router-eu-1 and us-east"));
        seedMetrics(apAlternative, 78, 0.22, 640, 18);

        // outages and cold paths
        NetworkPath usToAp = networkPathRepository.save(new NetworkPath(
                app, usEast, apSouth, "us-east-to-ap-south-primary", 2, "Direct Pacific link"));
        seedMetrics(usToAp, 310, 11.50, 180, 18);

        NetworkPath euToCore = networkPathRepository.save(new NetworkPath(
                app, euWest, euCoreRouter, "eu-west-to-core-router", 1, "Local aggregation link"));

        logger.debug("Seeded payments platform with paths {} and {}",
                apPrimary.getPathName(), euToCore.getPathName());
    }

    private void seedStreamingPlatform(User owner) {
        Application app = applicationRepository.save(new Application(
                "Media Delivery",
                "Video segment delivery and CDN origin traffic",
                owner));

        Endpoint usWest = endpointRepository.save(
                new Endpoint(app, "cdn-us-west-2a", "10.40.1.21", "us-west-2"));
        Endpoint euNorth = endpointRepository.save(
                new Endpoint(app, "cdn-eu-north-1a", "10.50.1.21", "eu-north-1"));

        NetworkPath origin = networkPathRepository.save(new NetworkPath(
                app, usWest, euNorth, "cdn-us-west-to-eu-north", 3, "Origin pull route"));
        seedMetrics(origin, 96, 0.20, 1250, 12);
    }

    private NetworkPath trafficPath(Application app, Endpoint source, Endpoint destination,
                                    String name, int hops, boolean primary, String description) {
        NetworkPath path = new NetworkPath(app, source, destination, name, hops, description);
        path.setIsPrimary(primary);
        return path;
    }

    /**
     * Generates a deterministic sample history with a mild drift so charts show shape rather than
     * noise. The path status is then derived through the same health authority real telemetry uses,
     * so the seeded estate cannot disagree with the classification rule.
     */
    private void seedMetrics(NetworkPath path, double avgLatencyMs, double avgPacketLossPct,
                             int throughputMbps, int samples) {
        Random random = new Random(path.getPathName().hashCode());
        Instant start = Instant.now().minus(samples * 6L, ChronoUnit.MINUTES);

        for (int i = 0; i < samples; i++) {
            double drift = 1.0 + 0.35 * Math.sin(i / 3.0);
            double latency = Math.max(5, avgLatencyMs * drift + random.nextDouble() * 8 - 4);
            double loss = Math.max(0, avgPacketLossPct * drift + random.nextDouble() * 0.15);

            PathMetric metric = new PathMetric(
                    path,
                    (int) Math.round(latency),
                    BigDecimal.valueOf(loss).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(throughputMbps * (0.9 + random.nextDouble() * 0.2))
                            .setScale(2, RoundingMode.HALF_UP)
            );
            metric.setTimestamp(start.plus(i * 6L, ChronoUnit.MINUTES));
            pathMetricRepository.save(metric);
        }

        pathHealthService.evaluateAndPersistStatus(path);
    }
}
