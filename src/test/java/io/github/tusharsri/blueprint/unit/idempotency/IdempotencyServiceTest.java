package io.github.tusharsri.blueprint.unit.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tusharsri.blueprint.config.AppProperties;
import io.github.tusharsri.blueprint.exception.ApiException;
import io.github.tusharsri.blueprint.idempotency.IdempotencyOutcome;
import io.github.tusharsri.blueprint.idempotency.IdempotencyService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdempotencyServiceTest {

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(new AppProperties(null, new AppProperties.Idempotency(10), null));
    }

    @Test
    void first_request_runs_the_action_and_reports_created() {
        AtomicInteger executions = new AtomicInteger();

        IdempotencyOutcome<String> outcome = service.execute("key-1", "payload-A", () -> {
            executions.incrementAndGet();
            return "result";
        });

        assertThat(outcome).isInstanceOf(IdempotencyOutcome.Created.class);
        assertThat(outcome.value()).isEqualTo("result");
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void same_key_and_payload_replays_the_stored_result_without_running_again() {
        AtomicInteger executions = new AtomicInteger();
        service.execute("key-1", "payload-A", () -> "first-" + executions.incrementAndGet());

        IdempotencyOutcome<String> replay = service.execute("key-1", "payload-A", () -> "second-" + executions.incrementAndGet());

        assertThat(replay).isInstanceOf(IdempotencyOutcome.Replayed.class);
        assertThat(replay.value()).isEqualTo("first-1");
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void same_key_with_a_different_payload_is_rejected_with_422() {
        service.execute("key-1", "payload-A", () -> "result");

        assertThatThrownBy(() -> service.execute("key-1", "payload-B", () -> "other"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(422);
                    assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
    }

    @Test
    void different_keys_are_independent() {
        IdempotencyOutcome<String> a = service.execute("key-a", "p", () -> "A");
        IdempotencyOutcome<String> b = service.execute("key-b", "p", () -> "B");

        assertThat(a).isInstanceOf(IdempotencyOutcome.Created.class);
        assertThat(b).isInstanceOf(IdempotencyOutcome.Created.class);
        assertThat(b.value()).isEqualTo("B");
    }

    @Test
    void a_failed_action_is_not_stored_so_the_client_can_retry() {
        assertThatThrownBy(() -> service.<String>execute("key-1", "p", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        IdempotencyOutcome<String> retry = service.execute("key-1", "p", () -> "recovered");

        assertThat(retry).isInstanceOf(IdempotencyOutcome.Created.class);
        assertThat(retry.value()).isEqualTo("recovered");
    }

    @Test
    void concurrent_requests_with_the_same_key_run_the_action_exactly_once() throws Exception {
        int threads = 20;
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IdempotencyOutcome<Integer>>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threads; i++) {
                Callable<IdempotencyOutcome<Integer>> task = () -> {
                    start.await();
                    return service.execute("shared-key", "payload", executions::incrementAndGet);
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
        }

        int created = 0;
        int replayed = 0;
        for (Future<IdempotencyOutcome<Integer>> future : futures) {
            IdempotencyOutcome<Integer> outcome = future.get();
            assertThat(outcome.value()).isEqualTo(1);
            if (outcome instanceof IdempotencyOutcome.Created<Integer>) {
                created++;
            } else {
                replayed++;
            }
        }
        assertThat(executions.get()).isEqualTo(1);
        assertThat(created).isEqualTo(1);
        assertThat(replayed).isEqualTo(threads - 1);
    }
}
