package org.conjur.jenkins.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.json.JSONObject;

import com.google.common.util.concurrent.UncheckedExecutionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory cache of Conjur access tokens, keyed by Conjur host identity, so that a fan-out of
 * parallel builds authenticates once instead of once per build. Heap only: never serialized, and
 * empty after a restart.
 *
 * <h3>Thread safety and zeroing guarantees</h3>
 * <ul>
 *   <li>The Guava {@code Cache.get(key, Callable)} contract ensures only ONE loader fires per key
 *       at a time; all other concurrent callers for the same key block on the same in-flight load.
 *       This eliminates the thundering-herd problem after key rotation or a cold start.</li>
 *   <li>{@code put} and {@code get} copy bytes in and out, so every caller owns a private array
 *       it is free to zero independently.</li>
 *   <li>A {@link RemovalListener} zeroes the stored byte array immediately when any entry leaves
 *       the cache (explicit eviction, expiry detection, or size-based eviction), so no token
 *       bytes linger in heap after removal.</li>
 *   <li>Expiry is checked manually on every {@code get} / {@code getOrLoad} call. The TOCTOU race
 *       on removal is closed by using {@code store.asMap().remove(key, entry)}, which atomically
 *       removes the entry only if it is still the exact same object — preventing a fresh entry
 *       loaded by a concurrent thread from being accidentally deleted.</li>
 * </ul>
 */
class ConjurTokenCache {

    private static final Logger LOGGER = Logger.getLogger(ConjurTokenCache.class.getName());

    /** Used only when the token's own {@code exp} claim cannot be read. */
    static final long DEFAULT_FALLBACK_TTL_SECONDS = 120;

    /**
     * How long before a token's real expiry we stop serving it, covering clock skew between
     * Jenkins and Conjur plus the latency of the request the token will be used for.
     */
    static final long TOKEN_CACHE_BUFFER_SECONDS = 30;

    /**
     * Hard ceiling on cache lifetime — Conjur's maximum configurable token lifetime is 5 hours.
     * Anything above this from {@code exp} indicates a misconfigured or unexpected token.
     */
    static final long MAX_CACHE_TTL_SECONDS = 5 * 3600L;

    /**
     * Functional interface for the authentication call. Declared separately so callers can pass
     * a lambda that throws {@link IOException} without any wrapping.
     */
    @FunctionalInterface
    interface AuthLoader {
        byte[] load() throws IOException;
    }

    /**
     * Control-flow carrier for the "token is valid but not worth caching" case (computed TTL
     * is zero or negative). Stack-trace capture is suppressed because this is never an error.
     */
    private static final class UncachedException extends Exception {
        private final byte[] token;

        UncachedException(byte[] token) {
            super(null, null, true, false);
            this.token = token;
        }

        /** Returns a caller-owned copy; zeros the original. */
        byte[] consumeToken() {
            byte[] copy = Arrays.copyOf(token, token.length);
            Arrays.fill(token, (byte) 0);
            return copy;
        }
    }

    /** Signals that the loader returned null (misconfigured authenticator). */
    private static final class NullTokenException extends Exception {
        NullTokenException() {
            super(null, null, true, false);
        }
    }

    /**
     * Supplies the current epoch second. Defaults to the real wall clock in production;
     * tests pass a controllable value via the package-private constructor.
     */
    private final LongSupplier nowSeconds;

    ConjurTokenCache() {
        // Lambda so Instant.now() is called fresh on every invocation (not captured once).
        this.nowSeconds = () -> Instant.now().getEpochSecond();
    }

    /** Package-private — for tests that need deterministic time control. */
    ConjurTokenCache(LongSupplier nowSeconds) {
        this.nowSeconds = nowSeconds;
    }

    /**
     * Guava cache. TTL is managed manually via {@link TokenEntry#expiresAtEpochSeconds} and
     * {@link #isEntryExpired}; {@code expireAfterWrite} acts as a hard ceiling that guarantees
     * ghost entries (deleted jobs, post-config-change stale tokens) are evicted and zeroed
     * within {@link #MAX_CACHE_TTL_SECONDS}, regardless of whether their key is ever accessed again.
     * {@code maximumSize} bounds heap in deployments with many distinct identities.
     * Guava's additional roles:
     * <ol>
     *   <li>In-flight deduplication via {@code Cache.get(key, Callable)}.</li>
     *   <li>Zeroing stored bytes via the {@link RemovalListener} on any removal path.</li>
     * </ol>
     */
    private final Cache<String, TokenEntry> store = CacheBuilder.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(MAX_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .removalListener((RemovalListener<String, TokenEntry>) n -> n.getValue().zero())
            .build();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns a caller-owned copy of the cached token for {@code key}. If the entry is absent
     * or expired, invokes {@code loader} exactly once (per key, across all concurrent callers)
     * and caches the result with a TTL derived from the token's own {@code exp} claim.
     *
     * <p>Concurrency guarantee: Guava's {@code Cache.get(key, Callable)} ensures that when
     * multiple threads simultaneously request the same absent key, exactly one loader invocation
     * fires; all other threads block until it completes and then receive the same result.
     *
     * @return caller-owned copy; the caller is responsible for zeroing it after use.
     *         Returns {@code null} when {@code loader} returns {@code null} (misconfigured
     *         authenticator — the authenticator has already logged why).
     * @throws IOException if {@code loader} throws
     */
    byte[] getOrLoad(String key, AuthLoader loader) throws IOException {
        // Fast path: valid cached entry — no loading needed.
        TokenEntry existing = store.getIfPresent(key);
        if (existing != null && !isEntryExpired(existing)) {
            byte[] copy = existing.copyToken();
            if (copy != null) return copy;
            // Entry was concurrently evicted (e.g. by the 401-retry path) between getIfPresent
            // and copyToken — fall through to reload so the caller is never served null.
        }

        // Expired entry: atomically remove THIS specific object so a concurrent fresh load
        // from another thread is not accidentally evicted.
        if (existing != null) {
            if (store.asMap().remove(key, existing)) {
                existing.zero(); // explicit zero; RemovalListener may also fire — idempotent
            }
        }

        // Slow path: Guava ensures only ONE Callable fires per absent key.
        // All other concurrent callers for the same key block here until the load completes.
        try {
            TokenEntry loaded = store.get(key, () -> {
                byte[] fresh = loader.load();
                if (fresh == null) {
                    throw new NullTokenException();
                }
                long ttl = computeTtl(fresh);
                try {
                    if (ttl <= 0) {
                        // Token is near or past its expiry — serve once without caching.
                        throw new UncachedException(Arrays.copyOf(fresh, fresh.length));
                    }
                    long expiresAt = nowSeconds.getAsLong() + ttl;
                    return new TokenEntry(Arrays.copyOf(fresh, fresh.length), expiresAt);
                } finally {
                    Arrays.fill(fresh, (byte) 0);
                }
            });

            // Guard: if an already-expired entry somehow survived (e.g. extreme clock skew),
            // evict it and serve the token once rather than returning null (which callers
            // treat as a hard authentication failure).
            if (isEntryExpired(loaded)) {
                byte[] token = loaded.copyToken();
                if (store.asMap().remove(key, loaded)) {
                    loaded.zero();
                }
                return token;
            }

            return loaded.copyToken();
            // Note: copyToken() can return null if loaded was evicted between the expiry check
            // and this call (same race as the fast path). Returning null here is acceptable:
            // the next caller will find the key absent and trigger a fresh load.

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UncachedException) {
                // Token is valid but not worth caching — return it directly to the caller.
                return ((UncachedException) cause).consumeToken();
            }
            if (cause instanceof NullTokenException) {
                return null;
            }
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new RuntimeException("Unexpected cache load failure for key [" + key + "]", cause);
        } catch (UncheckedExecutionException e) {
            throw new RuntimeException(
                    "Unexpected cache load failure for key [" + key + "]", e.getCause());
        }
    }

    /**
     * Evicts the entry for {@code key} and zeroes its stored bytes immediately.
     * The {@link RemovalListener} handles the zero so this method is a simple invalidate call.
     * Used to recover from Conjur rejecting a token the cache still considered valid (401 path).
     */
    void evict(String key) {
        store.invalidate(key);
    }

    // -------------------------------------------------------------------------
    // TTL helpers
    // -------------------------------------------------------------------------

    /**
     * Derives the cache lifetime from the token's own {@code exp} claim, so it adapts to whatever
     * {@code host_authorization_token_ttl} the Conjur operator configured.
     *
     * @return seconds to cache for; 0 or less means use the token once without caching it
     */
    long computeTtl(byte[] token) {
        long expSeconds = extractExpSeconds(token);
        if (expSeconds == 0) {
            return DEFAULT_FALLBACK_TTL_SECONDS;
        }
        long ttl = expSeconds - nowSeconds.getAsLong() - TOKEN_CACHE_BUFFER_SECONDS;
        return Math.min(Math.max(ttl, 0), MAX_CACHE_TTL_SECONDS);
    }

    /**
     * Returns {@code true} when the entry's expiry has passed according to the instance clock.
     * Replaces {@link TokenEntry#isExpired()} so that tests can inject a controllable clock.
     */
    private boolean isEntryExpired(TokenEntry entry) {
        return nowSeconds.getAsLong() >= entry.expiresAtEpochSeconds;
    }

    /**
     * Reads the {@code exp} claim out of a Conjur access token.
     *
     * <p>Conjur returns a Slosilo JWS as {@code {"protected":..,"payload":..,"signature":..}},
     * and the authenticators Base64-encode that JSON before handing it back, so there are two
     * encodings to peel off. The MIME decoder handles the outer layer because the authenticators
     * encode {@code withoutPadding()}; the inner {@code payload} is base64url with padding.
     *
     * @return the expiry as epoch seconds, or 0 when the token cannot be parsed
     */
    static long extractExpSeconds(byte[] token) {
        try {
            byte[] rawBody = Base64.getMimeDecoder().decode(token);
            String payloadB64 = new JSONObject(new String(rawBody, StandardCharsets.UTF_8))
                    .getString("payload");
            byte[] claimsBytes = Base64.getUrlDecoder().decode(payloadB64);
            return new JSONObject(new String(claimsBytes, StandardCharsets.UTF_8))
                    .getLong("exp");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "Cannot parse exp claim from Conjur access token; using fallback TTL", e);
            return 0L;
        }
    }

    // -------------------------------------------------------------------------
    // TokenEntry
    // -------------------------------------------------------------------------

    static final class TokenEntry {
        /** Guarded by {@code this}; set to null after zeroing. */
        private byte[] token;
        final long expiresAtEpochSeconds;

        TokenEntry(byte[] token, long expiresAtEpochSeconds) {
            this.token = token;
            this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        }

        synchronized byte[] copyToken() {
            return token == null ? null : Arrays.copyOf(token, token.length);
        }

        synchronized void zero() {
            if (token != null) {
                Arrays.fill(token, (byte) 0);
                token = null;
            }
        }
    }
}
