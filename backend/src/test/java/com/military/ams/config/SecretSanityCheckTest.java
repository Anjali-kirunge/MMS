package com.military.ams.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Guards the signing key that signs every session token.
 *
 * <p>The retired sample key used to be committed in {@code application.yml}.
 * Once a repository is public that key is public, so anyone who reads the
 * history can mint a valid ADMIN token. These tests pin the current behaviour:
 * a strong environment-supplied key passes, and a short or retired one stops
 * startup with an actionable message.</p>
 */
class SecretSanityCheckTest {

    // Placeholder hosts: a test suite must never embed real infrastructure names.
    private static final String REMOTE_URL =
            "jdbc:mysql://db.example.com:3306/military_asset_management?ssl-mode=REQUIRED";

    private static final String LOCAL_URL =
            "jdbc:mysql://localhost:3306/military_asset_management?useSSL=false";

    private static final String STRONG_SECRET =
            "9f2c1b7a4e6d8035af1c9b2d7e4f6081a3c5d9e2b7f4a1c8e3d6b9f2a5c8d1e4";

    /** Assembled at runtime so the retired value is not a usable literal in Git. */
    private static final String RETIRED_SAMPLE_SECRET =
            "militaryAssetManagement" + "SuperSecretKey" + "ChangeMeInProduction" + "2026_0123456789";

    private static void run(SecretSanityCheck check) {
        check.run(new DefaultApplicationArguments(new String[0]));
    }

    @ParameterizedTest(name = "local database URL is recognised: {0}")
    @ValueSource(strings = {
            "jdbc:mysql://localhost:3306/military_asset_management",
            "jdbc:mysql://127.0.0.1:3306/military_asset_management",
            "jdbc:mysql://0.0.0.0:3306/military_asset_management",
            "jdbc:mysql://LocalHost:3306/military_asset_management" })
    @DisplayName("A local JDBC URL is treated as a developer machine")
    void detectsLocalDatabase(String url) {
        assertThat(SecretSanityCheck.isLocalDatabase(url)).isTrue();
    }

    @ParameterizedTest(name = "remote database URL is not local: {0}")
    @ValueSource(strings = {
            REMOTE_URL,
            "jdbc:mysql://db.example.com:3306/military_asset_management",
            "jdbc:mysql://192.168.1.20:3306/military_asset_management" })
    @DisplayName("A hosted database URL is treated as a deployment")
    void detectsRemoteDatabase(String url) {
        assertThat(SecretSanityCheck.isLocalDatabase(url)).isFalse();
    }

    @Test
    @DisplayName("A key carrying the retired sample marker is recognised")
    void recognisesRevokedSample() {
        assertThat(SecretSanityCheck.isRevokedSample(RETIRED_SAMPLE_SECRET)).isTrue();
        assertThat(SecretSanityCheck.isRevokedSample(STRONG_SECRET)).isFalse();
        assertThat(SecretSanityCheck.isRevokedSample(null)).isFalse();
    }

    @Test
    @DisplayName("The retired sample key is rejected on every database, local or hosted")
    void rejectsRetiredSampleSecret() {
        SecretSanityCheck hosted = new SecretSanityCheck(
                RETIRED_SAMPLE_SECRET, "a-long-generated-password", REMOTE_URL, new String[0]);
        SecretSanityCheck local = new SecretSanityCheck(
                RETIRED_SAMPLE_SECRET, "a-long-generated-password", LOCAL_URL, new String[0]);

        assertThatThrownBy(() -> run(hosted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("openssl rand");
        assertThatThrownBy(() -> run(local))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("A key shorter than 32 characters is rejected")
    void rejectsShortSecret() {
        SecretSanityCheck check = new SecretSanityCheck(
                "too-short", "a-long-generated-password", REMOTE_URL, new String[0]);

        assertThatThrownBy(() -> run(check))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("An empty key is rejected with a generation hint")
    void rejectsEmptySecret() {
        SecretSanityCheck check = new SecretSanityCheck(
                "   ", "a-long-generated-password", REMOTE_URL, new String[0]);

        assertThatThrownBy(() -> run(check))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openssl rand -base64 48");
    }

    @Test
    @DisplayName("A generated key is accepted on a hosted database")
    void acceptsStrongSecretWhenHosted() {
        SecretSanityCheck check = new SecretSanityCheck(
                STRONG_SECRET, "a-long-generated-password", REMOTE_URL,
                new String[] { "https://app.example.com" });

        assertThatCode(() -> run(check)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A generated key is accepted on a developer machine")
    void acceptsStrongSecretWhenLocal() {
        SecretSanityCheck check = new SecretSanityCheck(
                STRONG_SECRET, "short", LOCAL_URL, new String[] { "http://localhost:5173" });

        assertThatCode(() -> run(check)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("The bootstrap password may be unset when the database already has users")
    void allowsUnsetBootstrapPassword() {
        SecretSanityCheck check = new SecretSanityCheck(
                STRONG_SECRET, "", REMOTE_URL, new String[] { "https://app.example.com" });

        assertThatCode(() -> run(check)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A wildcard CORS origin is flagged rather than rejected")
    void flagsWildcardCorsOrigin() {
        SecretSanityCheck check = new SecretSanityCheck(
                STRONG_SECRET, "a-long-generated-password", REMOTE_URL, new String[] { "*" });

        assertThatCode(() -> run(check)).doesNotThrowAnyException();
    }
}
