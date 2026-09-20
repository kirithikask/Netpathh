package com.netpath.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * First-operator provisioning for a database that has never been used.
 *
 * <p>This is deliberately separate from {@code app.demo-data}: demo data fabricates a whole estate
 * of paths, endpoints and telemetry, which must never happen in production. This creates one
 * operator account and nothing else, so a freshly migrated production database is actually usable.
 *
 * <p>Off by default. Enable it for the first boot against an empty database, then turn it back off.
 */
@Component
@ConfigurationProperties(prefix = "app.bootstrap-admin")
public class BootstrapAdminProperties {

    private boolean enabled = false;
    private String email = "";
    private String password = "";
    private String name = "NETPATH Operator";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
