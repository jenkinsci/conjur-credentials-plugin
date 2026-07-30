package org.conjur.jenkins.disco.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.conjur.jenkins.disco.e2e.config.OnboardSecretConfiguration;
import org.conjur.jenkins.disco.e2e.utils.Waiter;

public class IdiraOnboardUnmanagedSecretPage extends BasePage {

    private static final String CREATE_AND_ONBOARD_BUTTON = "//button[@data-testid='create-and-promote-button']";
    private static final String AUTHENTICATOR_STATUS = "//div[@data-testid='authenticator-resource-status']//span[text()='%s']";
    private static final String WORKLOAD_STATUS = "//div[@data-testid='workload-resource-status']//span[text()='%s']";
    private static final String SECRET_STATUS = "//div[@data-testid='secrets-resource-status']//span[text()='%s']";
    private static final String SERVICE_INFO_XPATH = "//span[text()='%s']/following-sibling::code";
    private static final String SECRET_PATH = "//div[contains(text(),'Conjur credential ID')]//span[contains(text(),'data/static')]";

    public IdiraOnboardUnmanagedSecretPage(Page page) {
        super(page);
    }

    public void clickCreateAndOnboard() {
        clickCreateAndOnboard(PARENT_FRAME_ID, DISCOVERY_CONTEXT_FRAME_ID);
    }

    public void clickCreateAndOnboard(String parentFrameId, String childFrameId) {
        executeInNestedIframes(parentFrameId, childFrameId, frame -> {
            var btn = frame.locator(CREATE_AND_ONBOARD_BUTTON).first();
            Waiter.waitUntil(() -> btn.isVisible(), (long) FRAME_WAIT_TIMEOUT_MS, 500);
            btn.click();
        });
    }

    public void waitForAuthenticatorStatus(String expectedStatus) {
        waitForAuthenticatorStatus(PARENT_FRAME_ID, DISCOVERY_CONTEXT_FRAME_ID, expectedStatus);
    }

    public void waitForAuthenticatorStatus(String parentFrameId, String childFrameId, String expectedStatus) {
        waitForStatusInFrame(parentFrameId, childFrameId, AUTHENTICATOR_STATUS, expectedStatus);
    }

    public void waitForWorkloadStatus(String expectedStatus) {
        waitForWorkloadStatus(PARENT_FRAME_ID, DISCOVERY_CONTEXT_FRAME_ID, expectedStatus);
    }

    public void waitForWorkloadStatus(String parentFrameId, String childFrameId, String expectedStatus) {
        waitForStatusInFrame(parentFrameId, childFrameId, WORKLOAD_STATUS, expectedStatus);
    }

    public void waitForSecretStatus(String expectedStatus) {
        waitForSecretStatus(PARENT_FRAME_ID, DISCOVERY_CONTEXT_FRAME_ID, expectedStatus);
    }

    public void waitForSecretStatus(String parentFrameId, String childFrameId, String expectedStatus) {
        waitForStatusInFrame(parentFrameId, childFrameId, SECRET_STATUS, expectedStatus);
    }

    private void waitForStatusInFrame(
            String parentFrameId,
            String childFrameId,
            String statusXpathTemplate,
            String expectedStatus
    ) {
        executeInNestedIframes(parentFrameId, childFrameId, frame -> {
            var statusLocator = frame.locator(String.format(statusXpathTemplate, expectedStatus)).first();
            Waiter.waitUntil(() -> statusLocator.isVisible(), (long) FRAME_WAIT_TIMEOUT_MS, 500);
        });
    }

    public String getOnboardSecretConfiguration(OnboardSecretConfiguration onboardSecretConfiguration) {
        return getFromNestedIframes(PARENT_FRAME_ID, DISCOVERY_CONTEXT_FRAME_ID, frame -> {
            var locator = frame.locator(String.format(SERVICE_INFO_XPATH, onboardSecretConfiguration.label)).first();
            Waiter.waitUntil(locator::isVisible, (long) FRAME_WAIT_TIMEOUT_MS, 500);

            var text = locator.textContent();
            return text.replace("\u00A0", "");
        });
    }

    public String getSecretPath(){
        return getFromNestedIframes(PARENT_FRAME_ID, DISCOVERY_CONTEXT_FRAME_ID, frame -> {
            var locator = frame.locator(SECRET_PATH);
            Waiter.waitUntil(locator::isVisible, (long) FRAME_WAIT_TIMEOUT_MS, 500);

            var text = locator.textContent();
            return text.replace("\u00A0", "");
        });
    }
}