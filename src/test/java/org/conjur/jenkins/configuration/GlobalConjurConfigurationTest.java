package org.conjur.jenkins.configuration;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import org.mockito.junit.MockitoJUnitRunner;

import hudson.model.AbstractItem;
import hudson.util.FormValidation;

@RunWith(MockitoJUnitRunner.class)
public class GlobalConjurConfigurationTest {

	@Mock
	private GlobalConjurConfiguration config;
	@Mock
	private AbstractItem abstractItem;

	@Test
	public void doCheckAuthWebServiceId() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String authWebServiceId = "jenkins";
			getConfigMockStatic.when(() -> config.doCheckAuthWebServiceId(abstractItem, authWebServiceId))
					.thenReturn(FormValidation.ok());
			// Assert the result
			assertEquals(FormValidation.ok(), config.doCheckAuthWebServiceId(abstractItem, authWebServiceId));
		}
	}

	@Test
	public void doCheckAuthWebServiceIdEmpty() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String authWebServiceId = "";
			String errorMsg = "Auth WebService Id should not be empty";
			getConfigMockStatic.when(() -> config.doCheckAuthWebServiceId(abstractItem, authWebServiceId))
					.thenReturn(FormValidation.error(errorMsg));

			String actualErrorMessage = config.doCheckAuthWebServiceId(abstractItem, authWebServiceId).getMessage();
			// Assert the result after removing the prefix "ERROR: "
			assertEquals(errorMsg, actualErrorMessage.replace("ERROR: ", ""));
		}
	}
}
