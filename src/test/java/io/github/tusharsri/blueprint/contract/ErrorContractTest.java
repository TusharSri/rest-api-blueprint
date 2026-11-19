package io.github.tusharsri.blueprint.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.ContractTest;
import io.github.tusharsri.blueprint.support.TestData;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Every error, whatever its cause, has the same envelope: clients can write one error handler. */
@ContractTest
class ErrorContractTest extends AbstractApiIntegrationTest {

    private static final Set<String> ENVELOPE = Set.of("status", "message", "timestamp", "error");
    private static final Set<String> ERROR = Set.of("code", "details", "path", "traceId");

    private static Set<String> keysAt(String json, String path) {
        Map<String, Object> node = read(json, path);
        return node.keySet();
    }

    private static void assertErrorEnvelope(MvcResult result, int expectedStatus) throws Exception {
        String body = bodyOf(result);
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        assertThat(keysAt(body, "$")).containsExactlyInAnyOrderElementsOf(ENVELOPE);
        assertThat(keysAt(body, "$.error")).containsExactlyInAnyOrderElementsOf(ERROR);
        assertThat(((Number) read(body, "$.status")).intValue()).isEqualTo(expectedStatus);
        assertThat((String) read(body, "$.error.traceId")).isNotBlank();
        assertThat((String) read(body, "$.error.code")).isNotBlank();
    }

    @ParameterizedTest(name = "{0} {1} as {2} -> {4}")
    @CsvSource(nullValues = "NULL", value = {
            "POST,  /api/products,           user,      {},    400",
            "POST,  /api/auth/login,         anonymous, {},    400",
            "GET,   /api/products/abc,       user,      NULL,  400",
            "GET,   /api/products/1,         anonymous, NULL,  401",
            "DELETE,/api/products/1,         user,      NULL,  403",
            "GET,   /api/products/999999,    user,      NULL,  404",
            "GET,   /api/unknown-endpoint,   user,      NULL,  404",
            "PATCH, /api/products,           user,      {},    405",
            "GET,   /api/demo/boom,          anonymous, NULL,  500"})
    void errors_share_one_envelope(String method, String path, String role, String body, int status) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method.trim()), path.trim());
        if (!"anonymous".equals(role.trim())) {
            request = auth(request, "admin".equals(role.trim()) ? adminToken() : userToken());
        }
        if (body != null) {
            request = request.contentType(JSON).content(body.trim());
        }
        assertErrorEnvelope(mvc.perform(request).andReturn(), status);
    }

    @Test
    void a_conflict_uses_the_same_envelope() throws Exception {
        String sku = TestData.uniqueSku();
        createProduct(userToken(), sku);

        MvcResult result = mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                .content(TestData.product().sku(sku).build())).andReturn();

        assertErrorEnvelope(result, 409);
    }

    @Test
    void an_idempotency_key_reuse_uses_the_same_envelope() throws Exception {
        String key = UUID.randomUUID().toString();
        String sku = TestData.uniqueSku();
        mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", key).contentType(JSON)
                .content(TestData.product().sku(sku).price("10.00").build()));

        MvcResult result = mvc.perform(auth(post("/api/products"), userToken()).header("Idempotency-Key", key)
                .contentType(JSON).content(TestData.product().sku(sku).price("55.00").build())).andReturn();

        assertErrorEnvelope(result, 422);
    }

    @Test
    void validation_errors_list_each_problem_as_field_and_message() throws Exception {
        String body = bodyOf(mvc.perform(auth(post("/api/products"), userToken()).contentType(JSON)
                .content(TestData.product().name("").price("-1").build())).andReturn());

        assertThat(keysAt(body, "$.error.details[0]")).containsExactlyInAnyOrder("field", "message");
    }
}
