package org.conjur.jenkins.disco.e2e.utils;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

public final class Waiter {

    private static final Logger LOG = Logger.getLogger(Waiter.class.getName());

    public static final long DEFAULT_TIMEOUT_MS  = 30_000;
    public static final long DEFAULT_INTERVAL_MS = 500;

    private Waiter() {}

    public static void waitUntil(BooleanSupplier condition, long timeoutMs, long intervalMs) {
        waitUntil(condition, timeoutMs, intervalMs, null);
    }

    public static void waitForVisible(Locator locator) {
        waitUntil(locator::isVisible, DEFAULT_TIMEOUT_MS, DEFAULT_INTERVAL_MS, null);
    }

    public static void waitForVisible(Locator locator, long timeoutMs, long intervalMs) {
        waitUntil(locator::isVisible, timeoutMs, intervalMs, null);
    }

    public static void waitForHidden(Locator locator, long timeoutMs, long intervalMs) {
        waitUntil(() -> !locator.isVisible(), timeoutMs, intervalMs, null);
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMs, long intervalMs, Page pageToRefresh) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (Exception e) {
                LOG.fine("[Waiter] Condition check threw (will retry): " + e.getMessage());
            }
            if (pageToRefresh != null) {
                pageToRefresh.reload();
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Waiter interrupted", e);
            }
        }
        throw new AssertionError("Condition was not satisfied within " + timeoutMs + " ms");
    }
}
