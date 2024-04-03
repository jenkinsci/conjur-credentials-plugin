package org.conjur.jenkins.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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
	public void doCheckTokenDurarionInSeconds() {
		// Mock parameters
		String tokenDurationInSeconds = "120";
		String keyLifetimeInMinutes = "60";

		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			getConfigMockStatic.when(() -> config.doCheckTokenDurarionInSeconds(abstractItem, tokenDurationInSeconds,
					keyLifetimeInMinutes)).thenReturn(FormValidation.ok());
			// Assert the result
			assertEquals(FormValidation.ok(),
					config.doCheckTokenDurarionInSeconds(abstractItem, tokenDurationInSeconds, keyLifetimeInMinutes));
		}

	}

	@Test
	public void doCheckTokenDurarionInSecondsNonNumericInput() {
		// Mock parameters
		String tokenDurationInSeconds = "120";
		String keyLifetimeInMinutes = "abc";

		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			getConfigMockStatic
					.when(() -> config.doCheckTokenDurarionInSeconds(abstractItem, tokenDurationInSeconds,
							keyLifetimeInMinutes))
					.thenReturn(FormValidation.error("Key lifetime and token duration must be numbers"));

			String actualErrorMessage = config
					.doCheckTokenDurarionInSeconds(abstractItem, tokenDurationInSeconds, keyLifetimeInMinutes)
					.getMessage();
			// Assert the result after removing the prefix "ERROR: "
			assertEquals("Key lifetime and token duration must be numbers", actualErrorMessage.replace("ERROR: ", ""));
		}

	}

	@Test
	public void doCheckTokenDurarionInSecondsTokenLongerThanKey() {
		// Mock parameters
		String tokenDurationInSeconds = "130";
		String keyLifetimeInMinutes = "120";

		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			getConfigMockStatic
					.when(() -> config.doCheckTokenDurarionInSeconds(abstractItem, tokenDurationInSeconds,
							keyLifetimeInMinutes))
					.thenReturn(FormValidation.error("Token cannot last longer than key"));

			String actualErrorMessage = config
					.doCheckTokenDurarionInSeconds(abstractItem, tokenDurationInSeconds, keyLifetimeInMinutes)
					.getMessage();
			// Assert the result after removing the prefix "ERROR: "
			assertEquals("Token cannot last longer than key", actualErrorMessage.replace("ERROR: ", ""));
		}
	}

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

	@Test
	public void doCheckJwtAudience() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String jwtAudience = "jenkins-server";
			getConfigMockStatic.when(() -> config.doCheckJwtAudience(abstractItem, jwtAudience))
					.thenReturn(FormValidation.ok());
			// Assert the result
			assertEquals(FormValidation.ok(), config.doCheckJwtAudience(abstractItem, jwtAudience));
		}
	}

	@Test
	public void testDoCheckJwtAudienceEmpty() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String jwtAudience = "";
			String errorMsg = "JWT Audience field should not be empty";
			getConfigMockStatic.when(() -> config.doCheckJwtAudience(abstractItem, jwtAudience))
					.thenReturn(FormValidation.error(errorMsg));

			String actualErrorMessage = config.doCheckJwtAudience(abstractItem, jwtAudience).getMessage();
			// Assert the result after removing the prefix "ERROR: "
			assertEquals(errorMsg, actualErrorMessage.replace("ERROR: ", ""));
		}
	}
	@Test
	public void doCheckIdentityFieldName() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String identityFieldName = "sub";
			getConfigMockStatic.when(() -> config.doCheckIdentityFieldName(abstractItem, identityFieldName))
					.thenReturn(FormValidation.ok());
			// Assert the result
			assertEquals(FormValidation.ok(), config.doCheckIdentityFieldName(abstractItem, identityFieldName));
		}
	}
	@Test
	public void doCheckIdentityFieldNameEmpty() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String identityFieldName = "";
			String errorMsg = "Identity Field Name should not be empty";
			getConfigMockStatic.when(() -> config.doCheckIdentityFieldName(abstractItem, identityFieldName))
					.thenReturn(FormValidation.error(errorMsg));

			String actualErrorMessage = config.doCheckIdentityFieldName(abstractItem, identityFieldName).getMessage();
			// Assert the result after removing the prefix "ERROR: "
			assertEquals(errorMsg, actualErrorMessage.replace("ERROR: ", ""));
		}
	}

	@Test
	public void doCheckIdentityFieldNameNonNumeric() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String identityFieldName = "test!@#";
			String errorMsg = "Identity Field Name should contain only alphanumeric characters";
			getConfigMockStatic.when(() -> config.doCheckIdentityFieldName(abstractItem, identityFieldName))
					.thenReturn(FormValidation.error(errorMsg));

			String actualErrorMessage = config.doCheckIdentityFieldName(abstractItem, identityFieldName).getMessage();
			// Assert the result after removing the prefix "ERROR: "
			assertEquals(errorMsg, actualErrorMessage.replace("ERROR: ", ""));
		}
	}

	@Test
	public void doCheckIdentityFormatFieldsFromToken() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String identityFormatFieldsFromToken = "aud,jenkins_full_name";
			getConfigMockStatic.when(
					() -> config.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken))
					.thenReturn(FormValidation.ok());
			// Assert the result
			assertEquals(FormValidation.ok(),
					config.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken));
		}
	}

	@Test
	public void doCheckIdentityFormatFieldsFromTokenWithComma() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String identityFormatFieldsFromToken = "aud, jenkins_full_name";
			String errorMsg = "Identity Format Fields can add additional fields with comma-delimited values : aud,jenkins_name,jenkins_full_name,jenkins_parent_full_name,<others>";
			getConfigMockStatic.when(
					() -> config.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken))
					.thenReturn(FormValidation.error(errorMsg));
			// Assert the result
			assertNotNull(config.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken));
		}
	}

	@Test
	public void doCheckIdentityFormatFieldsFromTokenEmpty() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String identityFormatFieldsFromToken = "";
			String errorMsg = "IdentityFormatFieldsFromToken field should not be empty";
			getConfigMockStatic.when(
					() -> config.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken))
					.thenReturn(FormValidation.error(errorMsg));
			String actualErrorMessage = config
					.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken).getMessage();
			// Assert the result after removing the prefix "ERROR: "
			assertEquals(errorMsg, actualErrorMessage.replace("ERROR: ", ""));
		}
	}

	@Test
	public void doCheckIdentityFormatFieldsFromTokenDuplicate() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String identityFormatFieldsFromToken = "jenkins_full_name,jenkins_full_name";
			String errorMsg = "Duplicate tokens found in IdentityFormatFieldsFromToken";
			getConfigMockStatic.when(
					() -> config.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken))
					.thenReturn(FormValidation.error(errorMsg));
			String actualErrorMessage = config
					.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken).getMessage();

			// Assert the result after removing the prefix "ERROR: "
			assertEquals(errorMsg, actualErrorMessage.replace("ERROR: ", ""));
		}
	}

	@Test
	public void doCheckIdentityFormatFieldsFromTokenJenkinsFullNameMissing() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String identityFormatFieldsFromToken = "jenkins_name,aud";
			String errorMsg = "Identity Format Fields must contain at least one of the jenkins_full_name or a combination of jenkins_parent_full_name and jenkins_name";
			getConfigMockStatic.when(
					() -> config.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken))
					.thenReturn(FormValidation.error(errorMsg));
			String actualErrorMessage = config
					.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken).getMessage();
			// Assert the result after removing the prefix "ERROR: "
			assertEquals(errorMsg, actualErrorMessage.replace("ERROR: ", ""));
		}
	}

	@Test
	public void doCheckIdentityFormatFieldsFromTokenCombinationMissing() {
		try (MockedStatic<GlobalConjurConfiguration> getConfigMockStatic = mockStatic(
				GlobalConjurConfiguration.class)) {
			String identityFormatFieldsFromToken = "jenkins_parent_full_name,aud";
			String errorMsg = "Identity Format Fields must contain at least one of the jenkins_full_name or a combination of jenkins_parent_full_name and jenkins_name";
			getConfigMockStatic.when(
					() -> config.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken))
					.thenReturn(FormValidation.error(errorMsg));
			String actualErrorMessage = config
					.doCheckIdentityFormatFieldsFromToken(abstractItem, identityFormatFieldsFromToken).getMessage();
			// Assert the result after removing the prefix "ERROR: "
			assertEquals(errorMsg, actualErrorMessage.replace("ERROR: ", ""));
		}
	}
}
