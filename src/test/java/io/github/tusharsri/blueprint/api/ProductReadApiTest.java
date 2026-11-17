package io.github.tusharsri.blueprint.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** GET endpoints: by id (path variable), list (query params: paging, filter, sort) and search. */
class ProductReadApiTest extends AbstractApiIntegrationTest {

    private String list(String... params) throws Exception {
        var request = auth(get("/api/products"), userToken());
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return bodyOf(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    // ---------- by id ----------

    @Test
    void get_by_id_returns_the_v1_shape() throws Exception {
        mvc.perform(auth(get("/api/products/1"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Sample product 1"))
                .andExpect(jsonPath("$.data.price").isNumber())
                .andExpect(jsonPath("$.data.category").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.sku").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void get_by_unknown_id_returns_404() throws Exception {
        mvc.perform(auth(get("/api/products/999999"), userToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Product with id 999999 not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void get_by_a_non_numeric_id_returns_400() throws Exception {
        mvc.perform(auth(get("/api/products/abc"), userToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    // ---------- list: paging ----------

    @Test
    void list_defaults_to_the_first_page_of_ten() throws Exception {
        mvc.perform(auth(get("/api/products"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.totalElements").isNumber())
                .andExpect(jsonPath("$.meta.totalPages").isNumber())
                .andExpect(jsonPath("$.meta.apiVersion").value("1"));
    }

    @Test
    void list_honours_page_and_size() throws Exception {
        String body = list("page", "1", "size", "5");

        assertThat(((Number) read(body, "$.meta.page")).intValue()).isEqualTo(1);
        assertThat(((Number) read(body, "$.meta.size")).intValue()).isEqualTo(5);
        assertThat((List<?>) read(body, "$.data")).hasSize(5);
        assertThat(((Number) read(body, "$.data[0].id")).longValue()).isEqualTo(6L);
    }

    @Test
    void list_beyond_the_last_page_is_empty_not_an_error() throws Exception {
        mvc.perform(auth(get("/api/products").param("page", "9999"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @ParameterizedTest(name = "{0}={1} is rejected with 400")
    @CsvSource({"size,0", "size,101", "size,-5", "page,-1", "size,abc", "page,abc"})
    void list_rejects_invalid_paging_parameters(String name, String value) throws Exception {
        mvc.perform(auth(get("/api/products").param(name, value), userToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").isNotEmpty());
    }

    // ---------- list: filter and sort ----------

    @Test
    void list_filters_by_category() throws Exception {
        String body = list("category", "BOOKS", "size", "100");

        List<String> categories = read(body, "$.data[*].category");
        assertThat(categories).isNotEmpty().allMatch("BOOKS"::equals);
    }

    @Test
    void list_rejects_an_unknown_category() throws Exception {
        mvc.perform(auth(get("/api/products").param("category", "SPACESHIPS"), userToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_sorts_by_price_descending() throws Exception {
        List<Number> prices = read(list("sort", "price,desc", "size", "30"), "$.data[*].price");

        assertThat(prices.stream().map(Number::doubleValue).toList()).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void list_sorts_by_id_ascending_by_default() throws Exception {
        List<Number> ids = read(list("size", "30"), "$.data[*].id");

        assertThat(ids.stream().map(Number::longValue).toList()).isSorted();
    }

    @Test
    void list_rejects_an_unsupported_sort_field() throws Exception {
        String body = bodyOf(mvc.perform(auth(get("/api/products").param("sort", "password,asc"), userToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andReturn());

        assertThat((String) read(body, "$.message")).contains("Unsupported sort field").contains("price");
    }

    // ---------- search ----------

    @Test
    void search_matches_case_insensitively() throws Exception {
        String body = bodyOf(mvc.perform(auth(get("/api/products/search").param("name", "SAMPLE PRODUCT 1"), userToken()))
                .andExpect(status().isOk())
                .andReturn());

        List<String> names = read(body, "$.data[*].name");
        assertThat(names).hasSizeGreaterThanOrEqualTo(11)
                .allSatisfy(n -> assertThat(n.toLowerCase()).contains("sample product 1"));
    }

    @Test
    void search_without_matches_returns_an_empty_list() throws Exception {
        mvc.perform(auth(get("/api/products/search").param("name", "no-such-product-xyz"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void search_requires_a_non_blank_name() throws Exception {
        mvc.perform(auth(get("/api/products/search"), userToken())).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/products/search").param("name", "  "), userToken())).andExpect(status().isBadRequest());
    }
}
