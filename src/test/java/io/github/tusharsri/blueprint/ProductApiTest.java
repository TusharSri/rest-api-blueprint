package io.github.tusharsri.blueprint;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ProductApiTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String login(String user, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private static String productJson(String sku, String price) {
        return "{\"name\":\"Test item\",\"price\":" + price + ",\"category\":\"BOOKS\",\"sku\":\"" + sku + "\",\"stock\":3}";
    }

    private static String uniqueSku() {
        return "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    void requestWithoutTokenIsRejectedWith401InStandardFormat() throws Exception {
        mvc.perform(get("/api/products/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.path").value("/api/products/1"));
    }

    @Test
    void versionHeaderSelectsResponseShape() throws Exception {
        String token = login("user", "user123");
        mvc.perform(get("/api/products/1").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").doesNotExist());
        mvc.perform(get("/api/products/1").header("Authorization", token).header("X-API-Version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").exists());
    }

    @Test
    void unknownProductReturns404() throws Exception {
        String token = login("user", "user123");
        mvc.perform(get("/api/products/999999").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidBodyReturns400WithFieldDetails() throws Exception {
        String token = login("user", "user123");
        mvc.perform(post("/api/products").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"price\":-1,\"category\":\"BOOKS\",\"sku\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details").isNotEmpty());
    }

    @Test
    void postWithSameIdempotencyKeyIsReplayedAndDifferentPayloadIsRejected() throws Exception {
        String token = login("user", "user123");
        String key = UUID.randomUUID().toString();
        String sku = uniqueSku();

        String first = mvc.perform(post("/api/products").header("Authorization", token)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "10.00")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse().getContentAsString();

        String second = mvc.perform(post("/api/products").header("Authorization", token)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "10.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
                (Integer) JsonPath.read(first, "$.data.id"), (Integer) JsonPath.read(second, "$.data.id"));

        mvc.perform(post("/api/products").header("Authorization", token)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "99.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void duplicateSkuReturns409() throws Exception {
        String token = login("user", "user123");
        String sku = uniqueSku();
        mvc.perform(post("/api/products").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(productJson(sku, "5.00"))).andExpect(status().isCreated());
        mvc.perform(post("/api/products").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "5.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRequiresAdminAndIsIdempotent() throws Exception {
        String user = login("user", "user123");
        String admin = login("admin", "admin123");
        mvc.perform(delete("/api/products/2").header("Authorization", user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(delete("/api/products/2").header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/products/2").header("Authorization", admin)).andExpect(status().isNoContent());
    }

    @Test
    void demoEndpointsReturnRequestedStatusAndCatchAll500() throws Exception {
        mvc.perform(get("/api/demo/status/429")).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/demo/status/204")).andExpect(status().isNoContent());
        mvc.perform(get("/api/demo/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }
}
