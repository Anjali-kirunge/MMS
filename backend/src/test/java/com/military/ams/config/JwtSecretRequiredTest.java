package com.military.ams.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.military.ams.security.JwtService;

/**
 * Guards the removal of the committed JWT signing key.
 *
 * <p>{@code application.yml} used to carry a fallback secret so a developer
 * could run the app with no setup. Because the repository is public, that key
 * was effectively published: anyone holding it could forge a token with any
 * role. {@code app.jwt.secret} is now {@code ${JWT_SECRET}} with no default, and
 * these tests prove the placeholder genuinely refuses to resolve when the
 * variable is absent, rather than trusting the YAML alone.</p>
 */
class JwtSecretRequiredTest {

    private static final String LONG_ENOUGH = "a-unique-signing-key-generated-with-openssl-rand-48-bytes";

    @Test
    @DisplayName("application.yml no longer contains a fallback JWT signing key")
    void applicationYamlHasNoDefaultSecret() throws IOException {
        List<org.springframework.core.env.PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application.yml",
                        new FileSystemResource("src/main/resources/application.yml"));

        assertThat(sources).as("application.yml must be loadable").isNotEmpty();

        Object raw = sources.get(0).getProperty("app.jwt.secret");
        assertThat(raw)
                .as("app.jwt.secret must be the bare placeholder ${JWT_SECRET}")
                .isEqualTo("${JWT_SECRET}");

        // Belt and braces: the old published key must not appear anywhere.
        String wholeFile = new String(
                java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get("src/main/resources/application.yml")),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(wholeFile)
                .doesNotContain("ChangeMeInProduction")
                .doesNotContain("militaryAssetManagementSuperSecretKey");
    }

    @Test
    @DisplayName("Resolving app.jwt.secret without JWT_SECRET throws instead of using a fallback")
    void placeholderIsUnresolvable() {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> overrides = new HashMap<>();
        // Exactly what application.yml now declares, with no JWT_SECRET anywhere.
        overrides.put("app.jwt.secret", "${JWT_SECRET}");
        environment.getPropertySources().addFirst(new MapPropertySource("test", overrides));

        assertThatThrownBy(() -> environment.getProperty("app.jwt.secret"))
                .isInstanceOf(org.springframework.util.PlaceholderResolutionException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("A supplied JWT_SECRET resolves normally")
    void placeholderResolvesWhenSupplied() {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("app.jwt.secret", LONG_ENOUGH);
        environment.getPropertySources().addFirst(new MapPropertySource("test", overrides));

        assertThat(environment.getProperty("app.jwt.secret")).isEqualTo(LONG_ENOUGH);
    }

    @Test
    @DisplayName("JwtService builds a working key from a supplied secret")
    void jwtServiceAcceptsSuppliedSecret() {
        assertThatCode(() -> new JwtService(LONG_ENOUGH, 43200000L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JwtService refuses an unresolved placeholder used as a literal key")
    void jwtServiceRejectsTheRawPlaceholder() {
        // Spring can hand the unresolved text straight through when nested
        // placeholders are left unexpanded. The JJWT key check then rejects it,
        // so a missing JWT_SECRET can never become a predictable signing key.
        assertThatThrownBy(() -> new JwtService("${JWT_SECRET}", 43200000L))
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }

    @Test
    @DisplayName("The startup guard rejects an unresolved placeholder too")
    void startupGuardRejectsTheRawPlaceholder() {
        SecretSanityCheck check = new SecretSanityCheck(
                "${JWT_SECRET}", "", "jdbc:mysql://db.example.com:3306/military_asset_management",
                new String[0]);

        assertThatThrownBy(() -> check.run(new org.springframework.boot.DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("The retired sample key is rejected by the deployment guard")
    void revokedSampleKeyRejected() {
        assertThat(SecretSanityCheck.isRevokedSample(
                "militaryAssetManagement" + "SuperSecretKey" + "ChangeMeInProduction" + "2026")).isTrue();
        assertThat(SecretSanityCheck.isRevokedSample(LONG_ENOUGH)).isFalse();
    }

    @Test
    @DisplayName("The packaged resource on the classpath carries no default key either")
    void noOtherYamlReintroducesDefault() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            if (in != null) {
                String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertThat(text).doesNotContain("ChangeMeInProduction");
            }
        }
    }
}
