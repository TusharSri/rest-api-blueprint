package io.github.tusharsri.blueprint.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tusharsri.blueprint.config.AppProperties;
import io.github.tusharsri.blueprint.dto.PageResult;
import io.github.tusharsri.blueprint.dto.ProductPatchRequest;
import io.github.tusharsri.blueprint.dto.ProductRequest;
import io.github.tusharsri.blueprint.exception.BadRequestException;
import io.github.tusharsri.blueprint.exception.ConflictException;
import io.github.tusharsri.blueprint.exception.ResourceNotFoundException;
import io.github.tusharsri.blueprint.model.Category;
import io.github.tusharsri.blueprint.model.Product;
import io.github.tusharsri.blueprint.repository.ProductRepository;
import io.github.tusharsri.blueprint.service.ProductService;
import io.github.tusharsri.blueprint.support.UnitTest;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Pure unit tests: real in-memory repository (30 seeded products, no latency), no Spring context, no cache. */
@UnitTest
class ProductServiceTest {

    private ProductService service;

    @BeforeEach
    void setUp() {
        ProductRepository repository = new ProductRepository(new AppProperties(null, null, new AppProperties.Repository(0)));
        service = new ProductService(repository);
    }

    private static ProductRequest request(String sku) {
        return new ProductRequest("  Widget  ", "A widget", new BigDecimal("12.50"), Category.HOME, sku, 4);
    }

    @Nested
    class GetAll {

        @Test
        void returns_first_page_with_paging_totals() {
            PageResult<Product> page = service.getAll(0, 10, null, "id,asc");

            assertThat(page.content()).extracting(Product::id)
                    .containsExactlyElementsOf(LongStream.rangeClosed(1, 10).boxed().toList());
            assertThat(page.page()).isZero();
            assertThat(page.size()).isEqualTo(10);
            assertThat(page.totalElements()).isEqualTo(30);
            assertThat(page.totalPages()).isEqualTo(3);
        }

        @Test
        void returns_last_page_and_an_empty_page_beyond_the_end() {
            assertThat(service.getAll(2, 10, null, "id,asc").content()).hasSize(10);
            PageResult<Product> beyond = service.getAll(3, 10, null, "id,asc");
            assertThat(beyond.content()).isEmpty();
            assertThat(beyond.totalElements()).isEqualTo(30);
        }

        @Test
        void rounds_total_pages_up_for_uneven_sizes() {
            PageResult<Product> page = service.getAll(4, 7, null, "id,asc");
            assertThat(page.totalPages()).isEqualTo(5);
            assertThat(page.content()).hasSize(2);
        }

        @Test
        void filters_by_category() {
            PageResult<Product> page = service.getAll(0, 100, Category.BOOKS, "id,asc");
            assertThat(page.totalElements()).isEqualTo(6);
            assertThat(page.content()).allSatisfy(p -> assertThat(p.category()).isEqualTo(Category.BOOKS));
        }

        @Test
        void sorts_by_price_descending() {
            PageResult<Product> page = service.getAll(0, 5, null, "price,desc");
            assertThat(page.content().get(0).price()).isEqualByComparingTo("95");
            assertThat(page.content()).extracting(Product::price).isSortedAccordingTo(Comparator.reverseOrder());
        }

        @Test
        void sorts_by_name_ignoring_case() {
            List<String> names = service.getAll(0, 30, null, "name,asc").content().stream().map(Product::name).toList();
            assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        }

        @Test
        void defaults_to_ascending_when_direction_is_omitted() {
            assertThat(service.getAll(0, 3, null, "id").content()).extracting(Product::id).containsExactly(1L, 2L, 3L);
        }

        @Test
        void rejects_unknown_sort_field() {
            assertThatThrownBy(() -> service.getAll(0, 10, null, "password,asc"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unsupported sort field");
        }

        @Test
        void rejects_unknown_sort_direction() {
            assertThatThrownBy(() -> service.getAll(0, 10, null, "id,sideways"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("asc");
        }
    }

    @Nested
    class GetById {

        @Test
        void returns_existing_product() {
            assertThat(service.getById(1L).name()).isEqualTo("Sample product 1");
        }

        @Test
        void throws_not_found_for_unknown_id() {
            assertThatThrownBy(() -> service.getById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product with id 999 not found");
        }
    }

    @Nested
    class Search {

        @Test
        void matches_case_insensitively_and_orders_by_id() {
            List<Product> found = service.searchByName("SAMPLE PRODUCT 1");
            assertThat(found).extracting(Product::id).containsExactly(1L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L);
        }

        @Test
        void returns_empty_list_when_nothing_matches() {
            assertThat(service.searchByName("does-not-exist")).isEmpty();
        }
    }

    @Nested
    class Create {

        @Test
        void assigns_id_trims_name_and_persists() {
            Product created = service.create(request("WIDGET-1"));

            assertThat(created.id()).isGreaterThan(30L);
            assertThat(created.name()).isEqualTo("Widget");
            assertThat(created.createdAt()).isEqualTo(created.updatedAt());
            assertThat(service.getById(created.id())).isEqualTo(created);
        }

        @Test
        void defaults_stock_to_zero_when_omitted() {
            ProductRequest noStock = new ProductRequest("Widget", null, BigDecimal.ONE, Category.TOYS, "NOSTOCK-1", null);
            assertThat(service.create(noStock).stock()).isZero();
        }

        @Test
        void rejects_duplicate_sku() {
            assertThatThrownBy(() -> service.create(request("SKU-001")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("SKU-001");
        }
    }

    @Nested
    class Replace {

        @Test
        void replaces_all_fields_but_keeps_id_and_creation_time() {
            Product before = service.getById(1L);
            Product after = service.replace(1L, request("REPLACED-1"));

            assertThat(after.id()).isEqualTo(1L);
            assertThat(after.createdAt()).isEqualTo(before.createdAt());
            assertThat(after.updatedAt()).isAfterOrEqualTo(before.createdAt());
            assertThat(after.name()).isEqualTo("Widget");
            assertThat(after.sku()).isEqualTo("REPLACED-1");
            assertThat(after.category()).isEqualTo(Category.HOME);
        }

        @Test
        void allows_keeping_its_own_sku() {
            assertThatCode(() -> service.replace(1L, request("SKU-001"))).doesNotThrowAnyException();
        }

        @Test
        void rejects_sku_that_belongs_to_another_product() {
            assertThatThrownBy(() -> service.replace(1L, request("SKU-002"))).isInstanceOf(ConflictException.class);
        }

        @Test
        void throws_not_found_for_unknown_id() {
            assertThatThrownBy(() -> service.replace(999L, request("X-1"))).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void is_idempotent_repeating_the_same_request_gives_the_same_state() {
            Product first = service.replace(1L, request("SAME-1"));
            Product second = service.replace(1L, request("SAME-1"));
            assertThat(second).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(first);
        }
    }

    @Nested
    class Patch {

        @Test
        void changes_only_the_provided_fields() {
            Product before = service.getById(5L);
            Product after = service.patch(5L, new ProductPatchRequest(null, null, new BigDecimal("1.25"), null, null));

            assertThat(after.price()).isEqualByComparingTo("1.25");
            assertThat(after).usingRecursiveComparison()
                    .ignoringFields("price", "updatedAt").isEqualTo(before);
        }

        @Test
        void keeps_everything_when_no_field_is_provided() {
            Product before = service.getById(5L);
            Product after = service.patch(5L, new ProductPatchRequest(null, null, null, null, null));
            assertThat(after).usingRecursiveComparison().ignoringFields("updatedAt").isEqualTo(before);
        }

        @Test
        void trims_the_name() {
            assertThat(service.patch(5L, new ProductPatchRequest("  New name  ", null, null, null, null)).name())
                    .isEqualTo("New name");
        }

        @Test
        void throws_not_found_for_unknown_id() {
            assertThatThrownBy(() -> service.patch(999L, new ProductPatchRequest(null, null, null, null, 1)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Delete {

        @Test
        void removes_the_product() {
            service.delete(3L);
            assertThatThrownBy(() -> service.getById(3L)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void is_idempotent_for_already_deleted_and_unknown_ids() {
            service.delete(3L);
            assertThatCode(() -> service.delete(3L)).doesNotThrowAnyException();
            assertThatCode(() -> service.delete(999L)).doesNotThrowAnyException();
        }
    }
}
