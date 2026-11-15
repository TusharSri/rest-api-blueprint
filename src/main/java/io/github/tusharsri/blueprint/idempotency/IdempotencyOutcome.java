package io.github.tusharsri.blueprint.idempotency;

/** Result of an idempotent execution: freshly created, or replayed from a previous identical request. */
public sealed interface IdempotencyOutcome<T> {
    T value();

    record Created<T>(T value) implements IdempotencyOutcome<T> {}

    record Replayed<T>(T value) implements IdempotencyOutcome<T> {}
}
