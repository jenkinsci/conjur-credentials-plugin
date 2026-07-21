package org.conjur.jenkins.disco.e2e.pages;

import com.microsoft.playwright.Page;
import org.conjur.jenkins.disco.e2e.utils.Waiter;

public class IdiraLoginPage extends BasePage {

    public IdiraLoginPage(Page page) {
        super(page);
    }

    public static final String USERNAME_INPUT = "input[name='username']";
    public static final String NEXT_BUTTON = "button:has-text('Next'):not(.disabled)";
    public static final String PASSWORD_INPUT = "input[name='answer'][type='password'][placeholder='Enter the password associated with your username']";
    public static final String NEXT_SUBMIT_BUTTON = "//div[@id='passwordForm']//button[@type='submit']";

    public void login(String username, String password) throws InterruptedException {
        page.locator(USERNAME_INPUT).first().fill(username);
        page.locator(NEXT_BUTTON).first().click();
        page.locator(PASSWORD_INPUT).first().fill(password);
        page.locator(IdiraLoginPage.NEXT_SUBMIT_BUTTON).first().click();
        Waiter.waitForHidden(page.locator(IDIRA_LOGO), 30_000, 500);
    }
}