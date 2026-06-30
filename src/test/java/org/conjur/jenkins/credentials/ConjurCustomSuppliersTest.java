package org.conjur.jenkins.credentials;

import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class ConjurCustomSuppliersTest {

    @Test
    public void memoizeWithExpiration_returnsCachedValueWithinWindow() {
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> base = () -> "v" + callCount.incrementAndGet();
        Supplier<String> memoized = ConjurCustomSuppliers.memoizeWithExpiration(base, Duration.ofSeconds(60));

        String first = memoized.get();
        String second = memoized.get();

        assertEquals(first, second);
        assertEquals(1, callCount.get());
    }

    @Test
    public void memoizeWithExpiration_refreshesAfterExpiry() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<String> base = () -> "v" + callCount.incrementAndGet();
        Supplier<String> memoized = ConjurCustomSuppliers.memoizeWithExpiration(base, Duration.ofMillis(50));

        String first = memoized.get();
        Thread.sleep(60);
        String second = memoized.get();

        assertNotEquals(first, second);
        assertEquals(2, callCount.get());
    }

    @Test
    public void memoizeWithExpiration_delegateCalledOnFirstGet() {
        AtomicInteger callCount = new AtomicInteger(0);
        Supplier<Integer> base = callCount::incrementAndGet;
        Supplier<Integer> memoized = ConjurCustomSuppliers.memoizeWithExpiration(base, Duration.ofSeconds(10));

        assertEquals(0, callCount.get());
        memoized.get();
        assertEquals(1, callCount.get());
    }

    @Test(expected = NullPointerException.class)
    public void memoizeWithExpiration_throwsOnNullDelegate() {
        ConjurCustomSuppliers.memoizeWithExpiration(null, Duration.ofSeconds(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void expiringSupplier_throwsOnZeroDuration() {
        Supplier<String> memoized = ConjurCustomSuppliers.memoizeWithExpiration(() -> "x", Duration.ZERO);
        memoized.get();
    }

    @Test(expected = IllegalArgumentException.class)
    public void expiringSupplier_throwsOnNegativeDuration() {
        Supplier<String> memoized = ConjurCustomSuppliers.memoizeWithExpiration(() -> "x", Duration.ofSeconds(-1));
        memoized.get();
    }
}
