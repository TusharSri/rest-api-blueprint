package io.github.tusharsri.blueprint.unit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.tusharsri.blueprint.config.AppProperties;
import io.github.tusharsri.blueprint.dto.TokenResponse;
import io.github.tusharsri.blueprint.security.JwtService;
import io.github.tusharsri.blueprint.support.UnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@UnitTest
class JwtServiceTest {

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static final SecretKey KEY = key("unit-test-secret-unit-test-secret-32b");

    private JwtService service;
    private JwtDecoder decoder;
    private UserDetails alice;

    @BeforeEach
    void setUp() {
        service = new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
                new AppProperties(new AppProperties.Jwt("unused", 30), null, null));
        decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
        alice = User.withUsername("alice").password("{noop}pw").roles("ADMIN", "USER").build();
    }

    @Test
    void returns_a_bearer_token_with_the_configured_lifetime() {
        TokenResponse response = service.issue(alice);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(Duration.ofMinutes(30).toSeconds());
    }

    @Test
    void token_carries_subject_roles_issuer_and_expiry() {
        Jwt jwt = decoder.decode(service.issue(alice).accessToken());

        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaimAsString("iss")).isNotBlank();
        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void a_tampered_token_is_rejected() {
        String token = service.issue(alice).accessToken();
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void a_token_signed_with_another_key_is_rejected() {
        JwtDecoder otherDecoder = NimbusJwtDecoder
                .withSecretKey(key("a-completely-different-secret-32bytes"))
                .macAlgorithm(MacAlgorithm.HS256).build();

        assertThatThrownBy(() -> otherDecoder.decode(service.issue(alice).accessToken()))
                .isInstanceOf(JwtException.class);
    }
}
