# First-build triage

This code was written without being compiled (no Maven Central access where it was generated).
If `./gradlew build` fails, check these in order. Each is a spot where Boot 4 / Spring Framework 7 APIs are newest.

| Symptom | Where | Likely fix |
|---|---|---|
| `cannot find symbol ApiVersionConfigurer` or `configureApiVersioning` | `config/WebConfig` | Check the class name/package in the Spring Framework 7 docs ("API versioning"); alternatively set `spring.mvc.apiversion.use.header=X-API-Version`, `spring.mvc.apiversion.supported=1,2`, `spring.mvc.apiversion.default=1` in `application.yml` and delete `WebConfig`. |
| `version` is not an attribute of `@GetMapping` | `ProductController` | Same as above: the Framework 7 version attribute. |
| `method does not override` in `GlobalExceptionHandler` | `handleMethodArgumentNotValid`, `handleExceptionInternal` | Compare the signatures with `ResponseEntityExceptionHandler` in your IDE (Ctrl+O) and adjust parameter types/nullability. |
| `package com.nimbusds... does not exist` | `config/SecurityConfig` | Add `implementation("com.nimbusds:nimbus-jose-jwt")` (version is managed by Boot). |
| Starter not found | `build.gradle.kts` | Use the exact names from start.spring.io for Boot 4.1. |
| Swagger UI shows only one of the v1/v2 operations | springdoc | Document versions with two `GroupedOpenApi` beans, or check springdoc 3.x release notes for API-versioning support. |
| `ProductApiTest.versionHeaderSelectsResponseShape` fails | test | Check which status an unsupported or missing version returns in Spring 7 and adjust. |
