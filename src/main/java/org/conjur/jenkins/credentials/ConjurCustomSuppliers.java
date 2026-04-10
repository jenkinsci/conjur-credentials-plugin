package org.conjur.jenkins.credentials;

import com.google.common.base.Preconditions;

import java.io.Serializable;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Adaptation of Guava's ExpiringMemoizingSupplier which adds lazy duration lookup functionality.
 *
 * @see <a href="https://github.com/google/guava/blob/v29.0/guava/src/com/google/common/base/Suppliers.java">Suppliers.java</a>
 */
final class ConjurCustomSuppliers {

    /**
     * Constructor
     */

    private ConjurCustomSuppliers()
    {

    }

    /**
     *
     * @param base supplier
     * @param duration expiration time
     * @return ExpiringMemoizingSupplier class
     * @param <T> class type
     */
    public static <T> Supplier<T> memoizeWithExpiration(
            Supplier<T> base, Duration duration) {
        return new ExpiringMemoizingSupplier<>(base, duration);
    }

    /**
     * Class used to hold Credentials in memory for period of time
     *
     * @param <T> object type
     */

    static class ExpiringMemoizingSupplier<T>
            implements Supplier<T>, Serializable {
        private static final long serialVersionUID = 0;
        final Supplier<T> delegate;
        final Duration duration;
        transient volatile T value;
        // The special value 0 means "not yet initialized".
        transient volatile long expirationNanos;

        /**
         * Constructor
         *
         * @param delegate
         * @param duration
         */
        ExpiringMemoizingSupplier(
                Supplier<T> delegate, Duration duration) {
            this.delegate = Preconditions.checkNotNull(delegate);
            this.duration = duration;
        }

        /**
         @return duration of expiry time
         **/
        private long getDurationNanos() {
            Duration d = duration;
            Preconditions.checkArgument(!d.isNegative() && !d.isZero());
            return d.toNanos();
        }

        /**
         * Overrided method which return expiration time in nanoseconds
         * @return expiration time in nanoseconds
         */
        @Override
        public T get() {
            // Another variant of Double Checked Locking.
            //
            // We use two volatile reads.  We could reduce this to one by
            // putting our fields into a holder class, but (at least on x86)
            // the extra memory consumption and indirection are more
            // expensive than the extra volatile reads.
            long nanos = expirationNanos;
            long now = System.nanoTime();
            if (nanos == 0 || now - nanos >= 0) {
                synchronized (this) {
                    if (nanos == expirationNanos) {  // recheck for lost race
                        T t = delegate.get();
                        value = t;
                        nanos = now + getDurationNanos();
                        // In the very unlikely event that nanos is 0, set it to 1;
                        // no one will notice 1 ns of tardiness.
                        expirationNanos = (nanos == 0) ? 1 : nanos;
                        return t;
                    }
                }
            }
            return value;
        }
    }
}
