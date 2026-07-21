package org.conjur.jenkins.disco.e2e.pages;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Page;
import org.conjur.jenkins.disco.e2e.utils.Waiter;

import java.util.function.Consumer;
import java.util.function.Function;

public abstract class BasePage {

    protected final Page page;

    protected static final double FRAME_WAIT_TIMEOUT_MS = 80_000;
    protected static final String DISCOVERY_CONTEXT_FRAME_ID = "discoverycontext-app-id";
    protected static final String PARENT_FRAME_ID = "shemanagespace";
    protected static final String IFRAME_BY_TESTID_SELECTOR_TEMPLATE = "//iframe[@data-testid='%s']";
    protected static final String IDIRA_LOGO = "//div[@class='idira-logo']";

    protected BasePage(Page page) {
        this.page = page;
    }

    protected void executeInFrame(String childFrameId, Consumer<FrameLocator> action) {
        executeInNestedIframes(PARENT_FRAME_ID, childFrameId, action);
    }

    /**
     * Executes actions inside a child iframe nested under a parent iframe.
     * Both frame identifiers are matched via iframe {@code data-testid}.
     */
    protected void executeInNestedIframes(String parentFrameId, String childFrameId, Consumer<FrameLocator> action) {
        var parentSelector = String.format(IFRAME_BY_TESTID_SELECTOR_TEMPLATE, parentFrameId);
        var childSelector = String.format(IFRAME_BY_TESTID_SELECTOR_TEMPLATE, childFrameId);

        Waiter.waitForVisible(page.locator(parentSelector).first(), (long) FRAME_WAIT_TIMEOUT_MS, 250);
        Waiter.waitForVisible(page.frameLocator(parentSelector).locator(childSelector).first(), (long) FRAME_WAIT_TIMEOUT_MS, 250);

        var childFrame = page.frameLocator(parentSelector).frameLocator(childSelector);
        action.accept(childFrame);
    }

    protected <T> T getFromNestedIframes(String parentFrameId, String childFrameId, Function<FrameLocator, T> function) {
        var parentSelector = String.format(IFRAME_BY_TESTID_SELECTOR_TEMPLATE, parentFrameId);
        var childSelector = String.format(IFRAME_BY_TESTID_SELECTOR_TEMPLATE, childFrameId);

        Waiter.waitForVisible(page.locator(parentSelector).first(), (long) FRAME_WAIT_TIMEOUT_MS, 250);
        Waiter.waitForVisible(page.frameLocator(parentSelector).locator(childSelector).first(), (long) FRAME_WAIT_TIMEOUT_MS, 250);

        var childFrame = page.frameLocator(parentSelector).frameLocator(childSelector);
        return function.apply(childFrame);
    }
}
