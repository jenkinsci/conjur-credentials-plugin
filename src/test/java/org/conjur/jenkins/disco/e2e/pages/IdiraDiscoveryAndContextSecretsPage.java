package org.conjur.jenkins.disco.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.conjur.jenkins.disco.e2e.utils.Waiter;

public class IdiraDiscoveryAndContextSecretsPage extends BasePage {

    private static final String SEARCH_INPUT_FIELD = "//input[contains(@placeholder,'Search')]";
    private static final String SECRET_NAME_IN_TABLE = "//span[text()='%s']";
    private static final String RISK_FINDING = "//span[@class = 'p-tabview-title' and contains(text(), 'Risk findings')]";
    private static final String ONBOARD_BUTTON = "//button[@data-testid='risk-card-onboard-button']";

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
}
