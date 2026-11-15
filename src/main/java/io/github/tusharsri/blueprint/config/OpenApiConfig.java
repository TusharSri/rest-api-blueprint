package io.github.tusharsri.blueprint.config;

import io.github.tusharsri.blueprint.util.ApiConstants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Boot REST API Blueprint")
                        .version("1.0.0")
                        .description("Reference API: CRUD, JWT auth, header versioning, idempotency, caching, error format."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    /** Documents the version header on every operation. */
    @Bean
    OperationCustomizer versionHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(new HeaderParameter()
                    .name(ApiConstants.VERSION_HEADER)
                    .description("API version (header-based versioning). Defaults to 1.")
                    .required(false)
                    .schema(new StringSchema()._enum(List.of("1", "2"))._default("1")));
            return operation;
        };
    }
}
