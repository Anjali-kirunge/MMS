package com.military.ams.config;

import java.net.URI;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

    /**
     * Validates the signing key and warns about weak deployment settings.
     *
     * <p>{@code application.yml} declares {@code app.jwt.secret} as a bare
     * {@code ${JWT_SECRET}}, so a missing value normally stops startup before this
     * runner executes. The check is still worth having: a value that resolves but
     * is unsuitable is exactly the case it covers, including a raw
     * {@code ${JWT_SECRET}} passed through unresolved, a key too short to be safe,
     * or the retired sample key that older commits and deployment logs may still
     * contain. It also flags the two settings most often forgotten on a real
     * deployment: a wildcard CORS origin and a weak bootstrap password.</p>
     */
@Component
public class SecretSanityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecretSanityCheck.class);

    /**
     * The retired key is no longer shipped in {@code application.yml}, so this
     * guard matches on a marker rather than reproducing the full literal. That
     * keeps a historical signing key out of the repository while still refusing
     * a key copied from an old commit or an old deployment log.
     */
    static final String REVOKED_SAMPLE_MARKER = "ChangeMeInProduction";

    static final int MINIMUM_SECRET_LENGTH = 32;
    static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final String jwtSecret;
    private final String adminPassword;
    private final String databaseUrl;
    private final String[] allowedOrigins;

    public SecretSanityCheck(@Value("${app.jwt.secret}") String jwtSecret,
                             @Value("${app.bootstrap.admin-password:}") String adminPassword,
                             @Value("${spring.datasource.url}") String databaseUrl,
                             @Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
        this.databaseUrl = databaseUrl;
        this.allowedOrigins = allowedOrigins == null ? new String[0] : allowedOrigins;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireStrongSigningKey();

        for (String origin : allowedOrigins) {
            if (origin != null && origin.trim().equals("*")) {
                log.warn("CORS_ALLOWED_ORIGINS contains '*'. List the real origins instead.");
                break;
            }
        }

        if (isLocalDatabase(databaseUrl)) {
            return;
        }

        if (adminPassword != null && !adminPassword.isBlank()
                && adminPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            log.warn("DEFAULT_ADMIN_PASSWORD is shorter than {} characters. Use a generated password "
                    + "for any deployment you care about.", MINIMUM_PASSWORD_LENGTH);
        }
    }

    private void requireStrongSigningKey() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is empty. Set it in the environment, for example: openssl rand -base64 48");
        }
        if (jwtSecret.trim().startsWith("${")) {
            throw new IllegalStateException(
                    "JWT_SECRET resolved to the placeholder text \"" + jwtSecret + "\", so the environment "
                            + "variable is not set. Export JWT_SECRET and restart.");
        }
        if (isRevokedSample(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is the retired sample key that earlier commits shipped. Anyone holding it can "
                            + "forge a valid ADMIN token. Generate a fresh key: openssl rand -base64 48");
        }
        if (jwtSecret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET is only " + jwtSecret.length() + " characters; at least "
                            + MINIMUM_SECRET_LENGTH + " are required. Generate one: openssl rand -base64 48");
        }
    }

    /**
     * A local JDBC URL marks a developer machine, where the weaker bootstrap
     * password warning is noise and the key length advice has already been
     * enforced by {@link #requireStrongSigningKey()}.
     */
    static boolean isLocalDatabase(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return false;
        }
        String remainder = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2);
        String host = remainder.split("[:/?]", 2)[0];
        String normalised = host.trim().toLowerCase(Locale.ROOT);
        return normalised.equals("localhost")
                || normalised.equals("0.0.0.0")
                || normalised.startsWith("127.");
    }

    /** A key carrying the retired sample marker is rejected wherever it appears. */
    static boolean isRevokedSample(String secret) {
        return secret != null && secret.contains(REVOKED_SAMPLE_MARKER);
    }
}
