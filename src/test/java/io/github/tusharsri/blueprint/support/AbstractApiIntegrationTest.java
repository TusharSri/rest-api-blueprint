package io.github.tusharsri.blueprint.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import io.github.tusharsri.blueprint.config.TraceIdFilter;

/**
 * Base class for API (integration) tests. All subclasses share ONE cached Spring context
 * (same configuration), so the whole API suite pays the startup cost once.
 * The full security filter chain is applied, so authentication and authorization are really exercised.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractApiIntegrationTest {

    protected static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final Map<String, String> TOKEN_CACHE = new ConcurrentHashMap<>();

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected TraceIdFilter traceIdFilter;

    protected MockMvc mvc;

    @BeforeEach
    void initMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(traceIdFilter)          // MockMvc does not pick up servlet Filter beans by itself
                .apply(springSecurity())            // must come after, so the trace id is set before security runs
                .build();
    }

    // ---------- authentication helpers ----------

    protected String userToken() {
        return token("user", "user123");
    }

    protected String adminToken() {
        return token("admin", "admin123");
    }

    protected String token(String username, String password) {
        return TOKEN_CACHE.computeIfAbsent(username, u -> {
            try {
                String body = mvc.perform(post("/api/auth/login").contentType(JSON)
                                .content(TestData.loginJson(username, password)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                return JsonPath.read(body, "$.data.accessToken");
            } catch (Exception e) {
                throw new IllegalStateException("Could not log in as " + username, e);
            }
        });
    }

    /** Adds "Authorization: Bearer <token>" to a request. */
    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    // ---------- data helpers ----------

    /** Creates a product with a unique SKU as the given user and returns its id. */
    protected long createProduct(String token) throws Exception {
        return createProduct(token, TestData.uniqueSku());
    }

    protected long createProduct(String token, String sku) throws Exception {
        String body = mvc.perform(auth(post("/api/products"), token).contentType(JSON)
                        .content(TestData.product().sku(sku).build()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    protected static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    protected static <T> T read(String json, String jsonPath) {
        return JsonPath.read(json, jsonPath);
    }
}
