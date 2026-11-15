package io.github.tusharsri.blueprint.idempotency;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.tusharsri.blueprint.config.AppProperties;
import io.github.tusharsri.blueprint.exception.ApiException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * In-memory Idempotency-Key store (TTL based).
 *  - first request with a key: runs the action and stores the result
 *  - same key + same payload: returns the stored result without running the action again
 *  - same key + different payload: 422
 * Failed actions are not stored, so the client can safely retry them.
 * For several app instances, back this with Redis or a database instead.
 */
@Service
public class IdempotencyService {

    private record Entry(String fingerprint, Object response) {}

    private final Cache<String, Entry> store;

    public IdempotencyService(AppProperties props) {
        this.store = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(props.idempotency().ttlMinutes()))
                .maximumSize(10_000)
                .build();
    }

    @SuppressWarnings("unchecked")
    public <T> IdempotencyOutcome<T> execute(String key, String fingerprint, Supplier<T> action) {
        AtomicBoolean created = new AtomicBoolean(false);
        // Cache.get(key, loader) is atomic per key: concurrent duplicates wait and then get the stored result
        Entry entry = store.get(key, k -> {
            created.set(true);
            return new Entry(fingerprint, action.get());
        });
        if (!entry.fingerprint().equals(fingerprint)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used with a different request payload");
        }
        T value = (T) entry.response();
        return created.get() ? new IdempotencyOutcome.Created<>(value) : new IdempotencyOutcome.Replayed<>(value);
    }
}
