package io.github.tusharsri.blueprint.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.dto.PageResult;
import io.github.tusharsri.blueprint.dto.ProductPatchRequest;
import io.github.tusharsri.blueprint.dto.ProductRequest;
import io.github.tusharsri.blueprint.exception.ResourceNotFoundException;
import io.github.tusharsri.blueprint.idempotency.IdempotencyOutcome;
import io.github.tusharsri.blueprint.idempotency.IdempotencyService;
import io.github.tusharsri.blueprint.model.Category;
import io.github.tusharsri.blueprint.model.Product;
import io.github.tusharsri.blueprint.service.ProductService;
import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.IntegrationTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-layer test: the real controller, validation, security filter chain and exception handler,
 * but with the service layer mocked. Proves the controller wires HTTP to the service correctly
 * (parameters, headers, status codes) and that security and validation stop a request before any business code runs.
 */
@IntegrationTest
class ProductControllerSliceTest extends AbstractApiIntegrationTest {

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private IdempotencyService idempotencyService;

    private static Product product(long id) {
        Instant now = Instant.now();
        return new Product(id, "SKU-" + id, "Mocked " + id, "description", new BigDecimal("12.50"),
                Category.BOOKS, 3, now, now);
    }

    // ---------- GET ----------

    @Test
    void list_passes_the_query_parameters_to_the_service_and_maps_the_page() throws Exception {
        given(productService.getAll(anyInt(), anyInt(), any(), anyString()))
                .willReturn(new PageResult<>(List.of(product(1)), 2, 5, 11, 3));

        mvc.perform(auth(get("/api/products").param("page", "2").param("size", "5")
                        .param("category", "TOYS").param("sort", "price,desc"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Mocked 1"))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(11))
                .andExpect(jsonPath("$.meta.totalPages").value(3));

        verify(productService).getAll(2, 5, Category.TOYS, "price,desc");
    }

    @Test
    void list_applies_the_documented_defaults() throws Exception {
        given(productService.getAll(anyInt(), anyInt(), any(), anyString()))
                .willReturn(new PageResult<>(List.of(), 0, 10, 0, 0));

        mvc.perform(auth(get("/api/products"), userToken())).andExpect(status().isOk());

        verify(productService).getAll(0, 10, null, "id,asc");
    }

    @Test
    void the_version_header_selects_the_response_shape_for_the_same_service_call() throws Exception {
        given(productService.getById(1L)).willReturn(product(1));

        mvc.perform(auth(get("/api/products/1"), userToken()))
                .andExpect(jsonPath("$.data.sku").doesNotExist());
        mvc.perform(auth(get("/api/products/1"), userToken()).header("X-API-Version", "2"))
                .andExpect(jsonPath("$.data.sku").value("SKU-1"));
    }

    @Test
    void a_service_not_found_exception_becomes_a_404_envelope() throws Exception {
        given(productService.getById(9L)).willThrow(new ResourceNotFoundException("Product", 9L));

        mvc.perform(auth(get("/api/products/9"), userToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---------- POST ----------

    @Test
    void post_without_a_key_calls_the_service_directly_and_skips_idempotency() throws Exception {
        given(productService.create(any(ProductRequest.class))).willReturn(product(42));

        MvcResult result = mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                        .content(TestData.fixture("product-valid.json")))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/products/42");
        verify(productService).create(any(ProductRequest.class));
        verifyNoInteractions(idempotencyService);
    }

    @Test
    void post_with_a_key_goes_through_the_idempotency_service_scoped_to_the_caller() throws Exception {
        given(idempotencyService.execute(anyString(), anyString(), any()))
                .willReturn(new IdempotencyOutcome.Replayed<>(product(7)));

        mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", "key-123").contentType(JSON)
                        .content(TestData.fixture("product-valid.json")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.data.id").value(7));

        ArgumentCaptor<String> scopedKey = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).execute(scopedKey.capture(), anyString(), any());
        assertThat(scopedKey.getValue()).startsWith("user:").contains("key-123");
        verify(productService, never()).create(any(ProductRequest.class));
    }

    @Test
    void an_invalid_body_is_rejected_before_any_business_code_runs() throws Exception {
        mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                        .content(TestData.product().name("").build()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService, idempotencyService);
    }

    // ---------- PUT / PATCH ----------

    @Test
    void put_passes_the_id_and_the_parsed_body_to_the_service() throws Exception {
        given(productService.replace(any(Long.class), any(ProductRequest.class))).willReturn(product(3));

        mvc.perform(auth(put("/api/products/3"), userToken()).contentType(JSON)
                        .content(TestData.fixture("product-update.json")))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductRequest> body = ArgumentCaptor.forClass(ProductRequest.class);
        verify(productService).replace(org.mockito.ArgumentMatchers.eq(3L), body.capture());
        assertThat(body.getValue().name()).isEqualTo("Fixture product (updated)");
        assertThat(body.getValue().category()).isEqualTo(Category.TOYS);
        assertThat(body.getValue().price()).isEqualByComparingTo("30.00");
    }

    @Test
    void patch_passes_only_the_fields_that_were_sent() throws Exception {
        given(productService.patch(any(Long.class), any(ProductPatchRequest.class))).willReturn(product(3));

        mvc.perform(auth(patch("/api/products/3"), userToken()).contentType(JSON).content("{\"price\":9.99}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductPatchRequest> body = ArgumentCaptor.forClass(ProductPatchRequest.class);
        verify(productService).patch(org.mockito.ArgumentMatchers.eq(3L), body.capture());
        assertThat(body.getValue().price()).isEqualByComparingTo("9.99");
        assertThat(body.getValue().name()).isNull();
        assertThat(body.getValue().stock()).isNull();
    }

    // ---------- DELETE ----------

    @Test
    void a_user_is_stopped_by_security_before_the_service_is_called() throws Exception {
        mvc.perform(auth(delete("/api/products/5"), userToken())).andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    void an_admin_delete_reaches_the_service_and_returns_204() throws Exception {
        mvc.perform(auth(delete("/api/products/5"), adminToken())).andExpect(status().isNoContent());

        verify(productService).delete(5L);
    }

    @Test
    void anonymous_requests_never_reach_the_service() throws Exception {
        mvc.perform(get("/api/products/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products").contentType(JSON).content("{}")).andExpect(status().isUnauthorized());

        verifyNoInteractions(productService, idempotencyService);
    }
}
