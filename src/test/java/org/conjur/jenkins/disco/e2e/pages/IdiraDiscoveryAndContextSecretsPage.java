package org.conjur.jenkins.disco.e2e.pages;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.conjur.jenkins.disco.e2e.utils.Waiter;

import java.util.logging.Logger;

public class IdiraDiscoveryAndContextSecretsPage extends BasePage {

    private static final Logger LOG = Logger.getLogger(IdiraDiscoveryAndContextSecretsPage.class.getName());

    private static final String SEARCH_INPUT_FIELD = "//input[contains(@placeholder,'Search')]";
    private static final String SECRET_NAME_IN_TABLE = "//span[text()='%s']";
    private static final String RISK_FINDING = "//span[@class = 'p-tabview-title' and contains(text(), 'Risk findings')]";
    private static final String ONBOARD_BUTTON = "//button[@data-testid='risk-card-onboard-button']";
    private static final String ONBOARDING_OVERLAY = "#onboarding-integrator-root";

    public IdiraDiscoveryAndContextSecretsPage(Page page) {
        super(page);
    }

    public void searchForSecret(String secretName) {
        searchForSecret(PARENT_FRAME_ID, DISCOVERY_CONTEXT_FRAME_ID, secretName);
    }

    public void searchForSecret(String parentFrameId, String childFrameId, String secretName) {
        executeInNestedIframes(parentFrameId, childFrameId, frame -> {
            var searchInput = frame.locator(SEARCH_INPUT_FIELD);
            Waiter.waitForVisible(searchInput);

            var found = false;
            var maxRetries = 3;
            var retryCount = 0;

            while (!found && retryCount < maxRetries) {
                searchInput.fill(secretName);

                var secretCell = frame.locator(String.format(SECRET_NAME_IN_TABLE, secretName)).first();

                try {
                    Waiter.waitForVisible(secretCell, 10_000, 500);

                    var cellText = secretCell.textContent();
                    if (cellText != null && cellText.equals(secretName)) {
                        dismissOnboardingOverlay(frame);
                        secretCell.click();
                        found = true;
                    } else {
                        searchInput.clear();
                        page.waitForTimeout(3000);
                        retryCount++;
                    }
                } catch (AssertionError e) {
                    searchInput.clear();
                    page.waitForTimeout(3000);
                    retryCount++;
                }
            }

            if (!found) {
                throw new AssertionError("Could not find secret '" + secretName + "' after " + maxRetries + " retries");
            }
        });
    }

    public void clickRiskFindingsTab() {
        clickRiskFindingsTab(PARENT_FRAME_ID, DISCOVERY_CONTEXT_FRAME_ID);
    }

    public void clickRiskFindingsTab(String parentFrameId, String childFrameId) {
        executeInNestedIframes(parentFrameId, childFrameId, frame -> {
            var riskTab = frame.locator(RISK_FINDING).first();
            Waiter.waitUntil(riskTab::isVisible, (long) FRAME_WAIT_TIMEOUT_MS, 500);
            riskTab.click();
        });
    }

    public void clickOnboardSecret() {
        clickOnboardSecret(PARENT_FRAME_ID, DISCOVERY_CONTEXT_FRAME_ID);
    }

    public void clickOnboardSecret(String parentFrameId, String childFrameId) {
        executeInNestedIframes(parentFrameId, childFrameId, frame -> {
            var btn = frame.locator(ONBOARD_BUTTON).first();
            Waiter.waitUntil(btn::isVisible, (long) FRAME_WAIT_TIMEOUT_MS, 500);
            btn.click();
        });
    }

    /**
     * Dismisses the onboarding integrator overlay ({@code #onboarding-integrator-root}) before a
     * click action.
     *
     * <p>The overlay uses the native HTML Popover API ({@code popover="manual"}) which places it
     * in the browser's <em>top layer</em> — above all regular stacking contexts. Because the
     * overlay is injected in the <strong>parent</strong> ({@code shemanagespace}) frame, its top
     * layer covers the entire child iframe ({@code discoverycontext-app-id}), intercepting every
     * pointer event sent into that child. Playwright reports this as the overlay "intercepts
     * pointer events" even though the target element is in the nested frame.
     *
     * <p>We probe all three frame levels so the fix is robust regardless of where the overlay
     * ends up: parent frame first (most likely), then the inner frame, then the main page. Each
     * probe is individually guarded so a missing overlay never throws.
     */
    private void dismissOnboardingOverlay(FrameLocator innerFrame) {
        var parentSelector = String.format(IFRAME_BY_TESTID_SELECTOR_TEMPLATE, PARENT_FRAME_ID);

        // Parent frame (shemanagespace) — most likely host of the CyberArk mascot overlay
        dismissOverlayIfPresent(page.frameLocator(parentSelector).locator(ONBOARDING_OVERLAY));
        // Inner frame (discoverycontext-app-id) — fallback in case the overlay moved
        dismissOverlayIfPresent(innerFrame.locator(ONBOARDING_OVERLAY));
        // Main page — last resort
        dismissOverlayIfPresent(page.locator(ONBOARDING_OVERLAY));
    }

    private void dismissOverlayIfPresent(Locator overlay) {
        try {
            if (overlay.count() > 0) {
                overlay.evaluate(
                        "el => { try { el.hidePopover(); } catch (e) { el.style.pointerEvents = 'none'; } }");
                LOG.fine("Dismissed onboarding overlay: " + overlay);
            }
        } catch (Exception e) {
            LOG.fine("Could not dismiss overlay: " + e.getMessage());
        }
    }
}
