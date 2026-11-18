package io.github.tusharsri.blueprint.unit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tusharsri.blueprint.dto.ApiResult;
import io.github.tusharsri.blueprint.dto.PageResult;
import io.github.tusharsri.blueprint.support.UnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class ApiResultTest {

    @Test
    void success_envelope_has_data_and_no_error() {
        ApiResult<String> result = ApiResult.success(201, "created", "payload", ApiResult.Meta.ofVersion("1"));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.message()).isEqualTo("created");
        assertThat(result.data()).isEqualTo("payload");
        assertThat(result.meta().apiVersion()).isEqualTo("1");
        assertThat(result.error()).isNull();
        assertThat(result.timestamp()).isNotNull();
    }

    @Test
    void ok_is_a_200_success() {
        assertThat(ApiResult.ok("fine", 42, null).status()).isEqualTo(200);
    }

    @Test
    void failure_envelope_has_error_and_no_data() {
        ApiResult.ErrorInfo error = new ApiResult.ErrorInfo("CODE", List.of(new ApiResult.FieldIssue("f", "m")), "/p", "t-1");

        ApiResult<Void> result = ApiResult.failure(400, "bad", error);

        assertThat(result.status()).isEqualTo(400);
        assertThat(result.data()).isNull();
        assertThat(result.meta()).isNull();
        assertThat(result.error().details()).hasSize(1);
        assertThat(result.error().traceId()).isEqualTo("t-1");
    }

    @Test
    void page_meta_is_built_from_a_page_result() {
        PageResult<String> page = new PageResult<>(List.of("a", "b"), 1, 2, 5, 3);

        ApiResult.Meta meta = ApiResult.Meta.ofPage(page, "2");

        assertThat(meta.page()).isEqualTo(1);
        assertThat(meta.size()).isEqualTo(2);
        assertThat(meta.totalElements()).isEqualTo(5);
        assertThat(meta.totalPages()).isEqualTo(3);
        assertThat(meta.apiVersion()).isEqualTo("2");
    }

    @Test
    void page_result_map_keeps_paging_information() {
        PageResult<Integer> mapped = new PageResult<>(List.of("a", "bb"), 0, 10, 2, 1).map(String::length);

        assertThat(mapped.content()).containsExactly(1, 2);
        assertThat(mapped.totalElements()).isEqualTo(2);
        assertThat(mapped.size()).isEqualTo(10);
    }
}
