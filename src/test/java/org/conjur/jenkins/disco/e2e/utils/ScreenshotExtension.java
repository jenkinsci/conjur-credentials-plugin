package org.conjur.jenkins.disco.e2e.utils;

import com.microsoft.playwright.Page;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class ScreenshotExtension extends TestWatcher {

    private static final Logger LOG = Logger.getLogger(ScreenshotExtension.class.getName());
    private static final String SCREENSHOT_DIR = "target/playwright-screenshots";

    private final Page page;

    public ScreenshotExtension(Page page) {
        this.page = page;
    }

    @Override
    protected void succeeded(Description description) {
        captureScreenshot("PASSED_" + description.getMethodName());
    }

    @Override
    protected void failed(Throwable e, Description description) {
        captureScreenshot("FAILED_" + description.getMethodName());
    }

    private void captureScreenshot(String screenshotName) {
        try {
            Path screenshotDir = Path.of(SCREENSHOT_DIR);
            Files.createDirectories(screenshotDir);
            Path screenshot = screenshotDir.resolve(screenshotName + ".png");
            page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(screenshot));
            LOG.info("[ScreenshotExtension] Screenshot saved: " + screenshot.toAbsolutePath());
        } catch (Exception e) {
            LOG.warning("[ScreenshotExtension] Screenshot capture failed: " + e.getMessage());
        }
    }
}
