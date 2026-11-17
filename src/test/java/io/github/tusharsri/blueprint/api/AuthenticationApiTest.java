package io.github.tusharsri.blueprint.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthenticationApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder; // signs with the application's (test profile) secret

    private static String encode(JwtEncoder encoder, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("user").issuedAt(issuedAt).expiresAt(expiresAt)
                .claim("roles", List.of("ROLE_USER")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    // ---------- login ----------

    @Test
    void login_with_valid_credentials_returns_a_bearer_token() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(JSON).content(TestData.loginJson("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void login_with_a_wrong_password_returns_401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(JSON).content(TestData.loginJson("user", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void login_with_an_unknown_user_gives_the_same_answer_as_a_wrong_password() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(JSON).content(TestData.loginJson("ghost", "whatever")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void login_with_blank_fields_returns_400_with_field_details() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(JSON).content(TestData.loginJson("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[?(@.field=='username')]").isNotEmpty())
                .andExpect(jsonPath("$.error.details[?(@.field=='password')]").isNotEmpty());
    }

    @Test
    void login_with_malformed_json_returns_400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void login_without_a_content_type_returns_415() throws Exception {
        mvc.perform(post("/api/auth/login").content(TestData.loginJson("user", "user123")))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---------- token handling on protected endpoints ----------

    @Test
    void request_without_a_token_returns_401_in_the_standard_format() throws Exception {
        mvc.perform(get("/api/products/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.path").value("/api/products/1"))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());
    }

    @Test
    void request_with_a_valid_token_succeeds() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken())).andExpect(status().isOk());
    }

    @Test
    void request_with_garbage_instead_of_a_token_returns_401() throws Exception {
        mvc.perform(auth(get("/api/products/1"), "not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void request_with_a_non_bearer_scheme_returns_401() throws Exception {
        mvc.perform(get("/api/products/1").header(AUTHORIZATION, "Basic dXNlcjp1c2VyMTIz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void request_with_an_expired_token_returns_401() throws Exception {
        Instant now = Instant.now();
        String expired = encode(jwtEncoder, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        mvc.perform(auth(get("/api/products/1"), expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void request_with_a_token_signed_by_someone_else_returns_401() throws Exception {
        JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("an-attackers-own-secret-32-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        String forged = encode(attacker, now, now.plus(1, ChronoUnit.HOURS));

        mvc.perform(auth(get("/api/products/1"), forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void the_token_issued_by_login_works_for_the_admin_account_too() throws Exception {
        assertThat(adminToken()).isNotBlank();
        mvc.perform(auth(get("/api/products/1"), adminToken())).andExpect(status().isOk());
    }
}
