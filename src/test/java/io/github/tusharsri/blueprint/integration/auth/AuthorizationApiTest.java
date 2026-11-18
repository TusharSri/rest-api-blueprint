package io.github.tusharsri.blueprint.integration.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.IntegrationTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Who may do what: anonymous, USER and ADMIN against every protected operation. */
@IntegrationTest
class AuthorizationApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE"})
    void anonymous_users_cannot_call_any_product_operation(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/api/products/1").contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void a_user_can_create_update_and_read_products() throws Exception {
        String token = userToken();
        long id = createProduct(token);

        mvc.perform(auth(put("/api/products/" + id), token).contentType(JSON).content(TestData.product().build()))
                .andExpect(status().isOk());
        mvc.perform(auth(patch("/api/products/" + id), token).contentType(JSON).content("{\"stock\":9}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/products/" + id), token)).andExpect(status().isOk());
    }

    @Test
    void a_user_cannot_delete_and_gets_403_in_the_standard_format() throws Exception {
        long id = createProduct(userToken());

        mvc.perform(auth(delete("/api/products/" + id), userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());

        // still there
        mvc.perform(auth(get("/api/products/" + id), userToken())).andExpect(status().isOk());
    }

    @Test
    void an_admin_can_delete() throws Exception {
        long id = createProduct(adminToken());

        mvc.perform(auth(delete("/api/products/" + id), adminToken())).andExpect(status().isNoContent());
    }

    @Test
    void a_valid_token_without_roles_can_read_but_not_delete() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("norole").issuedAt(now).expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("roles", List.of()).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mvc.perform(auth(get("/api/products/1"), token)).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/products/1"), token)).andExpect(status().isForbidden());
    }

    // ---------- actuator and docs ----------

    @Test
    void health_is_public() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void cache_actuator_endpoint_is_admin_only() throws Exception {
        mvc.perform(get("/actuator/caches")).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/actuator/caches"), userToken())).andExpect(status().isForbidden());
        mvc.perform(auth(get("/actuator/caches"), adminToken())).andExpect(status().isOk());
    }

    @Test
    void metrics_actuator_endpoint_is_admin_only() throws Exception {
        mvc.perform(auth(get("/actuator/metrics"), userToken())).andExpect(status().isForbidden());
        mvc.perform(auth(get("/actuator/metrics"), adminToken())).andExpect(status().isOk());
    }

    @Test
    void the_openapi_document_is_public() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
