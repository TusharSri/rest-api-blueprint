package io.github.tusharsri.blueprint.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.ContractTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the exact JSON shape clients depend on. Adding, renaming or removing a field fails these tests,
 * so every contract change is a deliberate decision (and a version bump), never an accident.
 */
@ContractTest
class ResponseSchemaContractTest extends AbstractApiIntegrationTest {

    private static Set<String> keysAt(String json, String path) {
        Map<String, Object> node = read(json, path);
        return node.keySet();
    }

    private String fetch(String url, String version) throws Exception {
        var request = auth(get(url), userToken());
        if (version != null) {
            request.header("X-API-Version", version);
        }
        return bodyOf(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    @Test
    void product_v1_has_exactly_these_fields() throws Exception {
        assertThat(keysAt(fetch("/api/products/1", "1"), "$.data"))
                .containsExactlyInAnyOrder("id", "name", "description", "price", "category", "createdAt", "updatedAt");
    }

    @Test
    void product_v2_is_v1_plus_sku_and_stock() throws Exception {
        assertThat(keysAt(fetch("/api/products/1", "2"), "$.data"))
                .containsExactlyInAnyOrder("id", "sku", "name", "description", "price", "category", "stock",
                        "createdAt", "updatedAt");
    }

    @Test
    void list_items_have_the_same_shape_as_single_items() throws Exception {
        assertThat(keysAt(fetch("/api/products?size=1", "1"), "$.data[0]"))
                .containsExactlyInAnyOrder("id", "name", "description", "price", "category", "createdAt", "updatedAt");
        assertThat(keysAt(fetch("/api/products?size=1", "2"), "$.data[0]"))
                .containsExactlyInAnyOrder("id", "sku", "name", "description", "price", "category", "stock",
                        "createdAt", "updatedAt");
    }

    @Test
    void the_success_envelope_has_exactly_these_fields() throws Exception {
        assertThat(keysAt(fetch("/api/products/1", null), "$"))
                .containsExactlyInAnyOrder("status", "message", "timestamp", "data", "meta");
    }

    @Test
    void paging_meta_has_exactly_these_fields() throws Exception {
        assertThat(keysAt(fetch("/api/products", null), "$.meta"))
                .containsExactlyInAnyOrder("page", "size", "totalElements", "totalPages", "apiVersion");
    }

    @Test
    void single_item_meta_only_carries_the_api_version() throws Exception {
        assertThat(keysAt(fetch("/api/products/1", "2"), "$.meta")).containsExactly("apiVersion");
    }

    @Test
    void field_types_are_stable() throws Exception {
        String body = fetch("/api/products/1", null);

        assertThat((Object) read(body, "$.data.id")).isInstanceOf(Number.class);
        assertThat((Object) read(body, "$.data.price")).isInstanceOf(Number.class);
        assertThat((Object) read(body, "$.data.name")).isInstanceOf(String.class);
        assertThat((Object) read(body, "$.status")).isInstanceOf(Integer.class);
        // timestamps are ISO-8601 instants
        assertThat(Instant.parse(read(body, "$.data.createdAt"))).isNotNull();
        assertThat(Instant.parse(read(body, "$.timestamp"))).isNotNull();
    }

    @Test
    void login_response_has_exactly_these_fields() throws Exception {
        String body = bodyOf(mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content(TestData.fixture("login-user.json")))
                .andExpect(status().isOk()).andReturn());

        assertThat(keysAt(body, "$.data")).containsExactlyInAnyOrder("accessToken", "tokenType", "expiresInSeconds");
    }

    @Test
    void a_created_product_is_returned_in_the_v1_shape_with_version_meta() throws Exception {
        String body = bodyOf(mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                        .content(TestData.fixture("product-valid.json")))
                .andExpect(status().isCreated()).andReturn());

        assertThat(keysAt(body, "$.data"))
                .containsExactlyInAnyOrder("id", "name", "description", "price", "category", "createdAt", "updatedAt");
        assertThat(keysAt(body, "$.meta")).containsExactly("apiVersion");
    }
}
