package io.github.tusharsri.blueprint.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.tusharsri.blueprint.support.AbstractApiIntegrationTest;
import io.github.tusharsri.blueprint.support.ContractTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Breaking-change guard for the published API description.
 *
 * The generated OpenAPI document is compared with a committed baseline
 * (src/test/resources/contract/openapi-baseline.json). Adding endpoints, responses or properties is fine.
 * REMOVING an endpoint, an HTTP method, a documented response code, a schema or a schema property fails the test,
 * because existing clients may depend on it.
 *
 * Create or refresh the baseline deliberately (after reviewing the diff in your pull request):
 *   Linux/macOS:  OPENAPI_BASELINE_UPDATE=true ./gradlew test --tests "*OpenApiBaselineContractTest"
 *   PowerShell:   $env:OPENAPI_BASELINE_UPDATE="true"; ./gradlew test --tests "*OpenApiBaselineContractTest"
 * Without a baseline file the test is skipped.
 */
@ContractTest
class OpenApiBaselineContractTest extends AbstractApiIntegrationTest {

    private static final Path BASELINE = Path.of("src/test/resources/contract/openapi-baseline.json");
    private static final List<String> HTTP_METHODS = List.of("get", "put", "post", "delete", "patch", "head", "options");

    @Test
    void the_api_has_not_lost_operations_responses_or_schema_properties() throws Exception {
        String actualJson = bodyOf(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());

        if ("true".equalsIgnoreCase(System.getenv("OPENAPI_BASELINE_UPDATE"))) {
            writeBaseline(actualJson);
            return;
        }
        Assumptions.assumeTrue(Files.exists(BASELINE),
                "No baseline yet: create it with OPENAPI_BASELINE_UPDATE=true (see the class comment)");

        Map<String, Object> baseline = JsonPath.parse(Files.readString(BASELINE)).json();
        Map<String, Object> actual = JsonPath.parse(actualJson).json();

        List<String> breaking = new ArrayList<>();
        comparePaths(baseline, actual, breaking);
        compareSchemas(baseline, actual, breaking);

        assertThat(breaking).as("breaking changes compared with %s", BASELINE).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object node) {
        return node instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static void comparePaths(Map<String, Object> baseline, Map<String, Object> actual, List<String> breaking) {
        Map<String, Object> actualPaths = map(actual.get("paths"));
        map(baseline.get("paths")).forEach((path, baselineItem) -> {
            Map<String, Object> actualItem = map(actualPaths.get(path));
            if (actualItem.isEmpty()) {
                breaking.add("endpoint removed: " + path);
                return;
            }
            map(baselineItem).forEach((method, baselineOperation) -> {
                if (!HTTP_METHODS.contains(method)) {
                    return;
                }
                Map<String, Object> actualOperation = map(actualItem.get(method));
                if (actualOperation.isEmpty()) {
                    breaking.add("operation removed: " + method.toUpperCase() + " " + path);
                    return;
                }
                Map<String, Object> actualResponses = map(actualOperation.get("responses"));
                map(map(baselineOperation).get("responses")).forEach((code, ignored) -> {
                    if (!actualResponses.containsKey(code)) {
                        breaking.add("response removed: " + method.toUpperCase() + " " + path + " -> " + code);
                    }
                });
            });
        });
    }

    private static void compareSchemas(Map<String, Object> baseline, Map<String, Object> actual, List<String> breaking) {
        Map<String, Object> actualSchemas = map(map(actual.get("components")).get("schemas"));
        map(map(baseline.get("components")).get("schemas")).forEach((name, baselineSchema) -> {
            Map<String, Object> actualSchema = map(actualSchemas.get(name));
            if (actualSchema.isEmpty()) {
                breaking.add("schema removed: " + name);
                return;
            }
            Map<String, Object> actualProperties = map(actualSchema.get("properties"));
            map(map(baselineSchema).get("properties")).forEach((property, ignored) -> {
                if (!actualProperties.containsKey(property)) {
                    breaking.add("property removed: " + name + "." + property);
                }
            });
        });
    }

    // ---------- baseline writer: sorted keys and indentation so the file diffs cleanly in pull requests ----------

    private static void writeBaseline(String json) throws IOException {
        Object parsed = JsonPath.parse(json).json();
        StringBuilder out = new StringBuilder();
        pretty(parsed, out, 0);
        Files.createDirectories(BASELINE.getParent());
        Files.writeString(BASELINE, out.append('\n').toString());
    }

    private static void pretty(Object value, StringBuilder out, int level) {
        String pad = "  ".repeat(level + 1);
        String closePad = "  ".repeat(level);
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> entry : new TreeMap<>(map).entrySet()) {
                out.append(pad).append(quote(String.valueOf(entry.getKey()))).append(": ");
                pretty(entry.getValue(), out, level + 1);
                out.append(++i < map.size() ? ",\n" : "\n");
            }
            out.append(closePad).append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.append("[]");
                return;
            }
            out.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                out.append(pad);
                pretty(list.get(i), out, level + 1);
                out.append(i + 1 < list.size() ? ",\n" : "\n");
            }
            out.append(closePad).append(']');
        } else if (value instanceof CharSequence text) {
            out.append(quote(text.toString()));
        } else {
            out.append(value);
        }
    }

    private static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return sb.append('"').toString();
    }
}
