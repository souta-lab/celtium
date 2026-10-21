package org.soutalab.celtium.vulkan;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Render-thread resource ownership. Replacing an owner does not invalidate queued or recorded draws.
 * Each accepted draw holds a lease until its submission completes (or the draw is discarded).
 */
final class LeasedResource<T> implements AutoCloseable {
    private final T value;
    private final Consumer<T> destroy;
    private int references = 1;
    private boolean ownerClosed;

    LeasedResource(T value, Consumer<T> destroy) {
        this.value = Objects.requireNonNull(value);
        this.destroy = Objects.requireNonNull(destroy);
    }

    Lease<T> acquire() {
        if (this.ownerClosed) {
            throw new IllegalStateException("resource owner is closed");
        }
        this.references++;
        return new Lease<>(this);
    }

    @Override
    public void close() {
        if (!this.ownerClosed) {
            this.ownerClosed = true;
            release();
        }
    }

    private void release() {
        if (--this.references == 0) {
            this.destroy.accept(this.value);
        }
    }

    static final class Lease<T> implements AutoCloseable {
        private LeasedResource<T> owner;

        private Lease(LeasedResource<T> owner) {
            this.owner = owner;
        }

        T value() {
            if (this.owner == null) {
                throw new IllegalStateException("resource lease is closed");
            }
            return this.owner.value;
        }

        @Override
        public void close() {
            LeasedResource<T> resource = this.owner;
            if (resource != null) {
                this.owner = null;
                resource.release();
            }
        }
    }
}
