package org.conjur.jenkins.disco.e2e.tests;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.conjur.jenkins.disco.e2e.config.DiscoE2eConfig;
import org.conjur.jenkins.disco.e2e.pages.IdiraDiscoveryAndContextSecretsPage;
import org.conjur.jenkins.disco.e2e.pages.IdiraLoginPage;
import org.conjur.jenkins.disco.e2e.pages.IdiraOnboardUnmanagedSecretPage;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import java.util.List;
import java.util.logging.Logger;

public abstract class PlaywrightTestBase {

    protected static final Logger LOG = Logger.getLogger(PlaywrightTestBase.class.getName());
    protected static final DiscoE2eConfig config = new DiscoE2eConfig();
    protected static Playwright playwright;
    protected static Browser browser;
    protected static Page page;
    protected static final String TARGET_URL = config.uiTargetUrl();
    protected static IdiraLoginPage idiraLoginPage;
    protected static IdiraDiscoveryAndContextSecretsPage discoveryPage;
    protected static IdiraOnboardUnmanagedSecretPage onboardPage;

    @BeforeClass
    public static void createContextAndPage() throws InterruptedException {
        boolean headless = config.isPlaywrightHeadless();
        playwright = Playwright.create();

        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setArgs(List.of("--window-size=1920,1080")));

        page = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent(config.playwrightUserAgent())
                        .setViewportSize(1920, 1080))
                .newPage();

        idiraLoginPage = new IdiraLoginPage(page);
        discoveryPage = new IdiraDiscoveryAndContextSecretsPage(page);
        onboardPage = new IdiraOnboardUnmanagedSecretPage(page);
    }

    @AfterClass
    public static void tearDown() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        LOG.info("[PlaywrightTestBase] Browser closed.");
    }
}
