package io.github.tusharsri.blueprint.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Test data helpers: unique SKUs and a small fluent builder for product JSON (no Jackson needed). */
public final class TestData {

    private TestData() {}

    /** Unique, valid SKU (matches ^[A-Z0-9-]{3,20}$) so tests never collide with each other. */
    public static String uniqueSku() {
        return "T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public static String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    /** Starts from a valid product; override or remove fields to build invalid variants. */
    public static ProductJson product() {
        return new ProductJson();
    }

    public static final class ProductJson {
        private final Map<String, String> fields = new LinkedHashMap<>();

        private ProductJson() {
            name("Test product").description("Created by an automated test").price("19.99")
                    .category("BOOKS").sku(uniqueSku()).stock(5);
        }

        public ProductJson name(String value) { return raw("name", quote(value)); }
        public ProductJson description(String value) { return raw("description", quote(value)); }
        public ProductJson price(String value) { return raw("price", value); }
        public ProductJson category(String value) { return raw("category", quote(value)); }
        public ProductJson sku(String value) { return raw("sku", quote(value)); }
        public ProductJson stock(int value) { return raw("stock", String.valueOf(value)); }
        public ProductJson without(String field) { fields.remove(field); return this; }

        /** Puts an already JSON-encoded value (numbers, null, etc.). */
        public ProductJson raw(String field, String jsonValue) { fields.put(field, jsonValue); return this; }

        public String build() {
            return fields.entrySet().stream()
                    .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                    .collect(Collectors.joining(",", "{", "}"));
        }

        private static String quote(String value) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
