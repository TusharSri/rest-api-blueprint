package io.github.tusharsri.blueprint.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.TestData;
import org.junit.jupiter.api.Test;

/** Header-based versioning: X-API-Version selects the response shape; no header means the default (v1). */
class VersioningApiTest extends AbstractApiIntegrationTest {

    private static final String VERSION = "X-API-Version";

    @Test
    void without_a_version_header_the_default_v1_is_served() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.apiVersion").value("1"))
                .andExpect(jsonPath("$.data.sku").doesNotExist())
                .andExpect(jsonPath("$.data.stock").doesNotExist());
    }

    @Test
    void version_1_returns_the_original_shape() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken()).header(VERSION, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.apiVersion").value("1"))
                .andExpect(jsonPath("$.data.sku").doesNotExist());
    }

    @Test
    void version_2_adds_sku_and_stock() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken()).header(VERSION, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.apiVersion").value("2"))
                .andExpect(jsonPath("$.data.sku").value("SKU-001"))
                .andExpect(jsonPath("$.data.stock").isNumber())
                .andExpect(jsonPath("$.data.name").value("Sample product 1"));
    }

    @Test
    void the_list_endpoint_is_versioned_too() throws Exception {
        mvc.perform(auth(get("/api/products"), userToken()).header(VERSION, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.apiVersion").value("2"))
                .andExpect(jsonPath("$.data[0].sku").isNotEmpty());
        mvc.perform(auth(get("/api/products"), userToken()).header(VERSION, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sku").doesNotExist());
    }

    @Test
    void an_unsupported_version_is_a_client_error_in_the_standard_format() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken()).header(VERSION, "99"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error.code").isNotEmpty())
                .andExpect(jsonPath("$.error.traceId").isNotEmpty());
    }

    @Test
    void version_independent_endpoints_ignore_the_header() throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).header(VERSION, "2").contentType(JSON)
                        .content(TestData.product().build()))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").header(VERSION, "2").contentType(JSON)
                        .content(TestData.loginJson("user", "user123")))
                .andExpect(status().isOk());
    }
}
