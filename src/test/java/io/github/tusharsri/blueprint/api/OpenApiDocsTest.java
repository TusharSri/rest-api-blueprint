package io.github.tusharsri.blueprint.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;

/** The generated OpenAPI document is part of the product: it must describe the API and its security. */
class OpenApiDocsTest extends AbstractApiIntegrationTest {

    @Test
    void the_document_is_an_openapi_3_spec_with_title_and_version() throws Exception {
        String body = bodyOf(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").isNotEmpty())
                .andExpect(jsonPath("$.info.version").isNotEmpty())
                .andReturn());

        assertThat((String) read(body, "$.openapi")).startsWith("3");
    }

    @Test
    void all_public_operations_are_documented() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/products']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/products/search']").exists())
                .andExpect(jsonPath("$.paths['/api/demo/status/{code}']").exists());
    }

    @Test
    void write_operations_document_their_error_responses() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/products'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post.responses['422']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete.responses['403']").exists());
    }

    @Test
    void the_jwt_bearer_security_scheme_is_declared() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void request_schemas_are_published() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ProductRequest").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest").exists());
    }
}
