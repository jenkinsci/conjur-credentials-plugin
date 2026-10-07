package org.conjur.jenkins.api;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;

public class ConjurTokenCacheTest {

    private static final String KEY = "https%3A%2F%2Fconjur%3A8443|authn|myAccount|host%2Ffrontend";
    private static final String OTHER_KEY = "https%3A%2F%2Fconjur%3A8443|authn|myAccount|host%2Fbackend";

    private ConjurTokenCache cache;

    @Before
    public void setUp() {
        cache = new ConjurTokenCache();
    }

    /** Builds a minimal Conjur Slosilo-style JWS token with a specific expiry, Base64-encoded. */
    private static byte[] buildToken(long expEpochSeconds) {
        String claims = "{\"sub\":\"host/frontend/frontend-01\",\"iat\":1693000000,\"exp\":"
                + expEpochSeconds + "}";
        String jws = "{\"protected\":\"eyJhbGciOiJjb25qdXIub3JnL3Nsb3NpbG8vdjIifQ\",\"payload\":\""
                + Base64.getUrlEncoder().encodeToString(claims.getBytes(StandardCharsets.UTF_8))
                + "\",\"signature\":\"c2lnbmF0dXJl\"}";
        return Base64.getEncoder().withoutPadding()
                .encodeToString(jws.getBytes(StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.US_ASCII);
    }

    /** Token that expires 8 minutes from now — well within the buffer window, giving a healthy TTL. */
    private static byte[] validToken() {
        return buildToken(Instant.now().getEpochSecond() + 480);
    }

    // -------------------------------------------------------------------------
    // Core getOrLoad contract
    // -------------------------------------------------------------------------

    @Test
    public void testGetOrLoadCallsLoaderOnMiss() throws IOException {
        AtomicInteger calls = new AtomicInteger(0);
        byte[] token = validToken();
        byte[] expected = token.clone(); // save before getOrLoad zeroes the raw bytes

        byte[] result = cache.getOrLoad(KEY, () -> {
            calls.incrementAndGet();
            return token;
        });

        assertArrayEquals("return value must equal the original bytes", expected, result);
        assertEquals("loader must fire exactly once on a miss", 1, calls.get());
    }

    @Test
    public void testGetOrLoadReturnsCacheHitWithoutCallingLoader() throws IOException {
        AtomicInteger calls = new AtomicInteger(0);
        byte[] token = validToken();

        cache.getOrLoad(KEY, () -> { calls.incrementAndGet(); return token; });
        cache.getOrLoad(KEY, () -> { calls.incrementAndGet(); return token; });
        cache.getOrLoad(KEY, () -> { calls.incrementAndGet(); return token; });

        assertEquals("loader must not fire on cache hits", 1, calls.get());
    }

    /**
     * Ownership rule: getOrLoad copies the token into the cache and zeroes the raw bytes that
     * the loader returned. Every subsequent call returns a fresh independent copy.
     */
    @Test
    public void testGetOrLoadCopiesTokenIntoCache() throws IOException {
        byte[] token = validToken();
        byte[] expected = token.clone();

        byte[] r1 = cache.getOrLoad(KEY, () -> token);
        byte[] r2 = cache.getOrLoad(KEY, () -> { throw new IOException("must not fire"); });

        assertArrayEquals("first return must equal original bytes", expected, r1);
        assertArrayEquals("second return must equal original bytes", expected, r2);
        assertNotSame("successive calls must return independent copies", r1, r2);
    }

    /** Zeroing a caller's copy must not invalidate the cache entry or affect other callers. */
    @Test
    public void testGetCopiesSoOneCallerCannotBlankAnothersToken() throws IOException {
        byte[] token = validToken();
        byte[] expected = token.clone();
        byte[] r1 = cache.getOrLoad(KEY, () -> token);
        Arrays.fill(r1, (byte) 0);  // zero caller's copy

        AtomicInteger calls = new AtomicInteger(0);
        byte[] r2 = cache.getOrLoad(KEY, () -> { calls.incrementAndGet(); return validToken(); });

        assertEquals("cache must not have been invalidated", 0, calls.get());
        assertArrayEquals("internal copy must be unaffected by caller zeroing", expected, r2);
    }

    /** Two distinct keys must never share a cache entry. */
    @Test
    public void testDifferentIdentitiesDoNotShareTokens() throws IOException {
        AtomicInteger loaderA = new AtomicInteger(0), loaderB = new AtomicInteger(0);
        cache.getOrLoad(KEY,       () -> { loaderA.incrementAndGet(); return validToken(); });
        cache.getOrLoad(OTHER_KEY, () -> { loaderB.incrementAndGet(); return validToken(); });
        // Repeated calls must be cache hits — loader must NOT fire again
        cache.getOrLoad(KEY,       () -> { loaderA.incrementAndGet(); return validToken(); });
        cache.getOrLoad(OTHER_KEY, () -> { loaderB.incrementAndGet(); return validToken(); });
        assertEquals("KEY must be a cache hit on second call",       1, loaderA.get());
        assertEquals("OTHER_KEY must be a cache hit on second call", 1, loaderB.get());
    }

    // -------------------------------------------------------------------------
    // Eviction
    // -------------------------------------------------------------------------

    /** After evict(), the loader must fire again on the next getOrLoad call. */
    @Test
    public void testEvictRemovesEntry() throws IOException {
        AtomicInteger calls = new AtomicInteger(0);
        cache.getOrLoad(KEY, () -> { calls.incrementAndGet(); return validToken(); });
        cache.evict(KEY);
        cache.getOrLoad(KEY, () -> { calls.incrementAndGet(); return validToken(); });
        assertEquals("loader must re-fire after eviction", 2, calls.get());
    }

    // -------------------------------------------------------------------------
    // Zeroing — TokenEntry.zero()
    // -------------------------------------------------------------------------

    /**
     * TokenEntry.zero() must overwrite all bytes with 0 and null the internal reference.
     * TokenEntry is a package-private static inner class; its constructor is package-private
     * and accessible here without any production test helper. The {@code token} field is
     * private; nulling is verified via {@code copyToken()}, which returns {@code null} when
     * the internal reference has been zeroed.
     * The RemovalListener wiring ({@code n -> n.getValue().zero()}) is a one-liner that calls
     * this now-tested method.
     */
    @Test
    public void testTokenEntryZeroNullsAndZeroesBytes() {
        byte[] bytes = {1, 2, 3, 4};
        ConjurTokenCache.TokenEntry entry = new ConjurTokenCache.TokenEntry(bytes, Long.MAX_VALUE);
        entry.zero();
        assertArrayEquals("zero() must overwrite bytes with zeros", new byte[4], bytes);
        assertNull("zero() must null the internal reference", entry.copyToken());
    }

    // -------------------------------------------------------------------------
    // Expiry — clock-controlled for exact, deterministic assertions
    // -------------------------------------------------------------------------

    /**
     * After the clock advances past an entry's expiresAt, the next getOrLoad must reload.
     * Uses an injectable clock so no real time elapses and the boundary is exact.
     */
    @Test
    public void testGetOrLoadReloadsAfterExpiry() throws IOException {
        AtomicLong clock = new AtomicLong(1_700_000_000L);
        ConjurTokenCache timeable = new ConjurTokenCache(clock::get);

        // Load initial token; expiresAt is stored as clock + computeTtl(token)
        timeable.getOrLoad(KEY, () -> buildToken(clock.get() + 480));

        // Advance clock well past expiresAt
        clock.addAndGet(600);

        AtomicInteger calls = new AtomicInteger(0);
        timeable.getOrLoad(KEY, () -> { calls.incrementAndGet(); return buildToken(clock.get() + 480); });
        assertEquals("loader must re-fire after expiry", 1, calls.get());
    }

    /**
     * When computeTtl() returns 0 (token near expiry), the token is returned to the caller
     * for one-time use but must not be stored in the cache.
     * Frozen clock makes the near-expiry boundary exact — no wall-clock races.
     */
    @Test
    public void testGetOrLoadDoesNotCacheWhenTtlIsZero() throws IOException {
        long frozenNow = 1_700_000_000L;
        ConjurTokenCache timeable = new ConjurTokenCache(() -> frozenNow);
        AtomicInteger calls = new AtomicInteger(0);
        // exp = frozenNow + 5, buffer = 30 → ttl = 5 - 30 = -25 → clamped to 0 → not cached
        byte[] nearExpired = buildToken(frozenNow + 5);

        byte[] result = timeable.getOrLoad(KEY, () -> { calls.incrementAndGet(); return nearExpired.clone(); });
        timeable.getOrLoad(KEY, () -> { calls.incrementAndGet(); return nearExpired.clone(); });

        assertNotNull("token should still be returned to the caller for one-time use", result);
        assertEquals("near-expired token must not be cached — loader fires on every call", 2, calls.get());
    }

    // -------------------------------------------------------------------------
    // Null and error paths
    // -------------------------------------------------------------------------

    /** getOrLoad returns null when the loader returns null, and null is never cached. */
    @Test
    public void testGetOrLoadReturnsNullWhenLoaderReturnsNull() throws IOException {
        AtomicInteger calls = new AtomicInteger(0);
        assertNull(cache.getOrLoad(KEY, () -> { calls.incrementAndGet(); return null; }));
        cache.getOrLoad(KEY, () -> { calls.incrementAndGet(); return null; });
        assertEquals("null token must not be cached — loader fires on every call", 2, calls.get());
    }

    /**
     * IOException from the loader must be propagated unwrapped — not wrapped in ExecutionException.
     */
    @Test
    public void testGetOrLoadPropagatesLoaderIOException() {
        IOException expected = new IOException("Conjur unreachable");

        assertThatThrownBy(() -> cache.getOrLoad(KEY, () -> { throw expected; }))
                .isInstanceOf(IOException.class)
                .isSameAs(expected);
    }

    /**
     * A RuntimeException from the loader is wrapped by Guava in UncheckedExecutionException,
     * which is not a subtype of ExecutionException. The catch block must handle it so callers
     * see a RuntimeException with the original cause, not UncheckedExecutionException.
     */
    @Test
    public void testGetOrLoadWrapsUncheckedExceptionFromLoader() {
        NullPointerException cause = new NullPointerException("authenticator internal error");

        assertThatThrownBy(() -> cache.getOrLoad(KEY, () -> { throw cause; }))
                .isInstanceOf(RuntimeException.class)
                .hasCause(cause);
    }

    /**
     * When an entry passes the post-load expiry check (extreme clock skew after a successful
     * Guava load), getOrLoad must return the token once rather than null, so the caller can
     * attempt to use it and rely on the 401-retry path if Conjur rejects it.
     */
    @Test
    public void testGetOrLoadServesTokenOnceWhenEntryExpiresImmediatelyAfterLoad() throws IOException {
        // Clock is behind during TTL computation (so the entry is stored), then jumps forward
        // past the expiry before the post-load check runs.
        long base = 1_700_000_000L;
        long tokenExp = base + 480; // 8 minutes of TTL at base time
        AtomicLong clock = new AtomicLong(base);
        ConjurTokenCache timeable = new ConjurTokenCache(clock::get);

        byte[] rawToken = buildToken(tokenExp);
        byte[] expected = rawToken.clone();

        // First call: clock is at base, TTL is positive, entry is stored.
        // Advance clock past expiry before the post-load guard runs.
        AtomicInteger calls = new AtomicInteger(0);
        byte[] result = timeable.getOrLoad(KEY, () -> {
            calls.incrementAndGet();
            clock.set(tokenExp + 1); // jump past expiry mid-load
            return rawToken.clone();
        });

        assertNotNull("expired entry must still be served once rather than returning null", result);
        assertArrayEquals("served token must match what the loader returned", expected, result);
        assertEquals(1, calls.get());
    }

    // -------------------------------------------------------------------------
    // Concurrency
    // -------------------------------------------------------------------------

    /**
     * Thundering herd: 10 threads all concurrently request the same absent key.
     * Guava's Cache.get(key, Callable) guarantees only ONE loader invocation fires.
     */
    @Test
    public void testGetOrLoadDeduplicatesConcurrentLoads() throws Exception {
        int threads = 10;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger loaderCount = new AtomicInteger(0);
        // Use a fixed known payload so we can compare the returned copies.
        byte[] expected = buildToken(Instant.now().getEpochSecond() + 480);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<byte[]>> futures = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await(); // all threads race to the cache simultaneously
                return cache.getOrLoad(KEY, () -> {
                    loaderCount.incrementAndGet();
                    // Simulate authentication latency so threads have time to pile up.
                    try { Thread.sleep(20); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    // Return a fresh clone so the loader doesn't share the `expected` reference.
                    return expected.clone();
                });
            }));
        }

        ready.await();   // wait for all threads to be ready
        start.countDown(); // release the herd

        for (Future<byte[]> f : futures) {
            assertArrayEquals("every caller should receive the correct token", expected, f.get());
        }
        pool.shutdown();

        assertEquals("loader must fire exactly once despite " + threads + " concurrent callers",
                1, loaderCount.get());
    }

    // -------------------------------------------------------------------------
    // TTL helpers — computeTtl and extractExpSeconds
    // -------------------------------------------------------------------------

    @Test
    public void testExtractExpSecondsReadsExpClaimFromConjurToken() {
        assertEquals(1693000480L, ConjurTokenCache.extractExpSeconds(buildToken(1693000480L)));
    }

    @Test
    public void testExtractExpSecondsReturnsZeroForUnparsableToken() {
        assertEquals(0L, ConjurTokenCache.extractExpSeconds(
                "not-a-conjur-token".getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * computeTtl reads the exp claim and subtracts the buffer.
     * Frozen clock makes the result exact — no tolerance needed.
     */
    @Test
    public void testComputeTtlReadsExpFromToken() {
        long frozenNow = 1_700_000_000L;
        ConjurTokenCache timeable = new ConjurTokenCache(() -> frozenNow);
        long ttl = timeable.computeTtl(buildToken(frozenNow + 90));
        assertEquals(90 - ConjurTokenCache.TOKEN_CACHE_BUFFER_SECONDS, ttl); // exactly 60
    }

    @Test
    public void testComputeTtlFallsBackForUnparsableToken() {
        ConjurTokenCache timeable = new ConjurTokenCache(() -> 1_700_000_000L);
        assertEquals(ConjurTokenCache.DEFAULT_FALLBACK_TTL_SECONDS,
                timeable.computeTtl("not-a-token".getBytes(StandardCharsets.US_ASCII)));
    }
}
