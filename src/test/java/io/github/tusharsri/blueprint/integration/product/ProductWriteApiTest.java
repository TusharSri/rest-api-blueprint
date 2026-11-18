package io.github.tusharsri.blueprint.integration.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.IntegrationTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** POST, PUT, PATCH and DELETE, including validation and conflict handling. */
@IntegrationTest
class ProductWriteApiTest extends AbstractApiIntegrationTest {

    // ======================= POST =======================

    @Test
    void post_creates_a_product_and_returns_201_with_a_location_header() throws Exception {
        MvcResult result = mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                        .content(TestData.product().name("  Widget  ").build()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("Widget"))
                .andExpect(jsonPath("$.data.sku").doesNotExist())
                .andReturn();

        long id = ((Number) read(bodyOf(result), "$.data.id")).longValue();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/products/" + id);

        mvc.perform(auth(get("/api/products/" + id), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Widget"));
    }

    @Test
    void post_without_stock_defaults_it_to_zero() throws Exception {
        long id = createProductFrom(TestData.product().without("stock").build());

        mvc.perform(auth(get("/api/products/" + id), userToken()).header("X-API-Version", "2"))
                .andExpect(jsonPath("$.data.stock").value(0));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("blank name", TestData.product().name("").build(), "name"),
                Arguments.of("missing name", TestData.product().without("name").build(), "name"),
                Arguments.of("name longer than 100", TestData.product().name("x".repeat(101)).build(), "name"),
                Arguments.of("zero price", TestData.product().price("0").build(), "price"),
                Arguments.of("negative price", TestData.product().price("-5").build(), "price"),
                Arguments.of("three decimals", TestData.product().price("10.999").build(), "price"),
                Arguments.of("missing price", TestData.product().without("price").build(), "price"),
                Arguments.of("missing category", TestData.product().without("category").build(), "category"),
                Arguments.of("lower-case sku", TestData.product().sku("abc-123").build(), "sku"),
                Arguments.of("sku too short", TestData.product().sku("A").build(), "sku"),
                Arguments.of("sku with a space", TestData.product().sku("AB CD").build(), "sku"),
                Arguments.of("missing sku", TestData.product().without("sku").build(), "sku"),
                Arguments.of("negative stock", TestData.product().stock(-1).build(), "stock"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void post_rejects_invalid_bodies_with_400_and_names_the_field(String ignoredCase, String body, String field)
            throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[?(@.field=='" + field + "')]").isNotEmpty());
    }

    @Test
    void post_rejects_an_unknown_category_value() throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                        .content(TestData.product().category("NOT_A_CATEGORY").build()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void post_rejects_malformed_json_and_an_empty_body() throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_rejects_a_duplicate_sku_with_409() throws Exception {
        String sku = TestData.uniqueSku();
        createProduct(userToken(), sku);

        mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                        .content(TestData.product().sku(sku).build()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("A product with SKU '" + sku + "' already exists"));
    }

    @Test
    void post_rejects_an_unsupported_media_type_with_415() throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void a_client_that_only_accepts_xml_gets_406() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken()).header(ACCEPT, MediaType.APPLICATION_XML_VALUE))
                .andExpect(status().isNotAcceptable());
    }

    // ======================= PUT =======================

    @Test
    void put_replaces_the_whole_product() throws Exception {
        long id = createProduct(userToken());
        String newSku = TestData.uniqueSku();

        mvc.perform(auth(put("/api/products/" + id), userToken()).contentType(JSON)
                        .content(TestData.product().name("Replaced").sku(newSku).price("99.50").category("TOYS").build()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("Replaced"))
                .andExpect(jsonPath("$.data.price").value(99.5))
                .andExpect(jsonPath("$.data.category").value("TOYS"));

        mvc.perform(auth(get("/api/products/" + id), userToken()).header("X-API-Version", "2"))
                .andExpect(jsonPath("$.data.sku").value(newSku));
    }

    @Test
    void put_is_idempotent_repeating_it_changes_nothing() throws Exception {
        long id = createProduct(userToken());
        String body = TestData.product().name("Same every time").build();

        String first = bodyOf(mvc.perform(auth(put("/api/products/" + id), userToken()).contentType(JSON).content(body))
                .andExpect(status().isOk()).andReturn());
        String second = bodyOf(mvc.perform(auth(put("/api/products/" + id), userToken()).contentType(JSON).content(body))
                .andExpect(status().isOk()).andReturn());

        assertThat((String) read(second, "$.data.name")).isEqualTo(read(first, "$.data.name"));
        assertThat((Object) read(second, "$.data.price")).isEqualTo(read(first, "$.data.price"));
        assertThat((Object) read(second, "$.data.createdAt")).isEqualTo(read(first, "$.data.createdAt"));
    }

    @Test
    void put_on_an_unknown_id_returns_404_and_does_not_create() throws Exception {
        mvc.perform(auth(put("/api/products/999999"), userToken()).contentType(JSON).content(TestData.product().build()))
                .andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/products/999999"), userToken())).andExpect(status().isNotFound());
    }

    @Test
    void put_with_a_sku_owned_by_another_product_returns_409() throws Exception {
        long id = createProduct(userToken());

        mvc.perform(auth(put("/api/products/" + id), userToken()).contentType(JSON)
                        .content(TestData.product().sku("SKU-001").build()))
                .andExpect(status().isConflict());
    }

    @Test
    void put_validates_the_body() throws Exception {
        long id = createProduct(userToken());

        mvc.perform(auth(put("/api/products/" + id), userToken()).contentType(JSON)
                        .content(TestData.product().name("").build()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='name')]").isNotEmpty());
    }

    // ======================= PATCH =======================

    @Test
    void patch_changes_only_the_provided_fields() throws Exception {
        long id = createProduct(userToken());
        String before = bodyOf(mvc.perform(auth(get("/api/products/" + id), userToken())).andReturn());

        mvc.perform(auth(patch("/api/products/" + id), userToken()).contentType(JSON).content("{\"price\":42.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(42.5))
                .andExpect(jsonPath("$.data.name").value((String) read(before, "$.data.name")));
    }

    @Test
    void patch_with_an_empty_object_changes_nothing() throws Exception {
        long id = createProduct(userToken());
        String before = bodyOf(mvc.perform(auth(get("/api/products/" + id), userToken())).andReturn());

        String after = bodyOf(mvc.perform(auth(patch("/api/products/" + id), userToken()).contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andReturn());

        assertThat((String) read(after, "$.data.name")).isEqualTo(read(before, "$.data.name"));
        assertThat((Object) read(after, "$.data.price")).isEqualTo(read(before, "$.data.price"));
    }

    @Test
    void patch_validates_the_provided_fields() throws Exception {
        long id = createProduct(userToken());

        mvc.perform(auth(patch("/api/products/" + id), userToken()).contentType(JSON).content("{\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='price')]").isNotEmpty());
    }

    @Test
    void patch_on_an_unknown_id_returns_404() throws Exception {
        mvc.perform(auth(patch("/api/products/999999"), userToken()).contentType(JSON).content("{\"stock\":1}"))
                .andExpect(status().isNotFound());
    }

    // ======================= DELETE =======================

    @Test
    void delete_removes_the_product() throws Exception {
        long id = createProduct(adminToken());

        mvc.perform(auth(delete("/api/products/" + id), adminToken())).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/products/" + id), adminToken())).andExpect(status().isNotFound());
    }

    @Test
    void delete_is_idempotent_deleting_again_still_returns_204() throws Exception {
        long id = createProduct(adminToken());

        mvc.perform(auth(delete("/api/products/" + id), adminToken())).andExpect(status().isNoContent());
        mvc.perform(auth(delete("/api/products/" + id), adminToken())).andExpect(status().isNoContent());
    }

    private long createProductFrom(String body) throws Exception {
        String response = bodyOf(mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn());
        return ((Number) read(response, "$.data.id")).longValue();
    }
}
