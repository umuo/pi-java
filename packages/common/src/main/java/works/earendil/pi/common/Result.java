package works.earendil.pi.common;

import java.util.Objects;
import java.util.function.Function;

public sealed interface Result<T, E> permits Result.Ok, Result.Err {
    boolean isOk();

    default boolean isErr() {
        return !isOk();
    }

    T orElseThrow(Function<E, ? extends RuntimeException> mapper);

    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    record Ok<T, E>(T value) implements Result<T, E> {
        public Ok {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public T orElseThrow(Function<E, ? extends RuntimeException> mapper) {
            return value;
        }
    }

    record Err<T, E>(E error) implements Result<T, E> {
        public Err {
            Objects.requireNonNull(error, "error");
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public T orElseThrow(Function<E, ? extends RuntimeException> mapper) {
            throw mapper.apply(error);
        }
    }
}
