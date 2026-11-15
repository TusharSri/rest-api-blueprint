package io.github.tusharsri.blueprint.config;

import io.github.tusharsri.blueprint.util.ApiConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Header-based API versioning (built into Spring Framework 7). Use @GetMapping(version = "2") etc. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader(ApiConstants.VERSION_HEADER)
                .addSupportedVersions("1", "2")
                .setDefaultVersion("1");
    }
}
