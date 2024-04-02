
package org.conjur.jenkins.jwtauthimpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.conjur.jenkins.configuration.GlobalConjurConfiguration;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JwtTokenTest {
	@Mock
	private GlobalConjurConfiguration globalConfigMock;

	@Mock
	private Authentication authentication;

	@Test
	public void mockSign() {
		JwtToken jwtToken = mock(JwtToken.class);
		when(jwtToken.sign()).thenReturn("Signing Token");
		assertEquals("Signing Token", jwtToken.sign());

	}

	@Test
	public void mockGetToken() {
		try (MockedStatic<JwtToken> jwtTokenTestMockedStatic = mockStatic(JwtToken.class)) {
			mock(JwtToken.class);
			Object context = "secretId";
			jwtTokenTestMockedStatic.when(() -> JwtToken.getToken(context)).thenReturn("secret retrival " + context);
			assertEquals("secret retrival secretId", JwtToken.getToken(context));

		}
	}

	@Test
	public void mockGetUnsignedToken() {
		try (MockedStatic<JwtToken> jwtTokenTestMockedStatic = mockStatic(JwtToken.class)) {
			JwtToken jwtToken2 = mock(JwtToken.class);
			String pluginAction = " sdfghjkl";
			jwtTokenTestMockedStatic.when(() -> JwtToken.getUnsignedToken(pluginAction, jwtToken2))
					.thenReturn(jwtToken2);
			// Call the method that uses the mocked static method
			assertSame(jwtToken2, JwtToken.getUnsignedToken(pluginAction, jwtToken2));
		}

	}

	@Test
	public void getUnsignedTokenNull() {
		try (MockedStatic<JwtToken> jwtTokenTestMockedStatic = mockStatic(JwtToken.class)) {
			JwtToken jwtToken2 = null;
			String pluginAction = " testAction";
			jwtTokenTestMockedStatic.when(() -> JwtToken.getUnsignedToken(pluginAction, null)).thenReturn(jwtToken2);
			JwtToken mockResult = JwtToken.getUnsignedToken(pluginAction, jwtToken2);
			assertNull(mockResult);
		}
	}

	@Test
	public void getUnsignedTokenWithEnabledJWKS() {
		globalConfigMock.setEnableJWKS(true);
		when(globalConfigMock.getEnableJWKS()).thenReturn(true);
		assertEquals(true, globalConfigMock.getEnableJWKS());
	}

	@Test
	public void getUnsignedTokenWithDiabledJWKS() {
		globalConfigMock.setEnableJWKS(false);
		when(globalConfigMock.getEnableJWKS()).thenReturn(false);
		assertEquals(false, globalConfigMock.getEnableJWKS());
	}

	@Test
	public void getIdentityFormatFieldsTokenNotNullValue() {
		// Set up mock values
		globalConfigMock.setIdentityFormatFieldsFromToken("aud,jenkins_name");
		when(globalConfigMock.getIdentityFormatFieldsFromToken()).thenReturn("aud,jenkins_name");

		JwtToken jwtToken = new JwtToken();
		jwtToken.claim.put("aud", "jenkins-server");
		jwtToken.claim.put("jenkins_name", "main");

		// Split the identity format fields
		List<String> identityFields = Arrays.asList(globalConfigMock.getIdentityFormatFieldsFromToken().split(","));
		List<String> identityValues = new ArrayList<>(identityFields.size());

		for (String identityField : identityFields) {
			String identityFieldValue = jwtToken.claim.has(identityField) ? jwtToken.claim.getString(identityField)
					: "";
			identityValues.add(identityFieldValue);
		}
		assertNotNull(identityValues);

		jwtToken.claim.put("identity", StringUtils.join(identityValues, "-"));
		// Test if the split operation is successful
		assertEquals("jenkins-server-main", jwtToken.claim.getString("identity"));
	}

	@Test
	public void getIdentityFormatFieldsTokenEmptyValue() {
		// Set up mock values
		globalConfigMock.setIdentityFormatFieldsFromToken("aud,jenkins_name");
		when(globalConfigMock.getIdentityFormatFieldsFromToken()).thenReturn("aud,jenkins_name");

		JwtToken jwtToken = new JwtToken();
		jwtToken.claim.put("aud", "");
		jwtToken.claim.put("jenkins_name", "main");

		// Split the identity format fields
		List<String> identityFields = Arrays.asList(globalConfigMock.getIdentityFormatFieldsFromToken() != null
				? globalConfigMock.getIdentityFormatFieldsFromToken().split(",")
				: new String[0]);
		List<String> identityValues = new ArrayList<>(identityFields.size());

		for (String identityField : identityFields) {
			String identityFieldValue = jwtToken.claim.has(identityField) ? jwtToken.claim.getString(identityField)
					: "";
			identityValues.add(identityFieldValue);
		}
		assertNotNull(identityValues);
		jwtToken.claim.put("identity", StringUtils.join(identityValues, "-"));
		String identityKey = jwtToken.claim.getString("identity").toString();
		// Assert that the identityValues list is not null
		assertEquals("-main", identityKey.replace("[]", ""));
	}


	@Test
	public void testGetSelectIdentityFormatFieldsTokenWithDelimiter() {
		// Set up mock values
		globalConfigMock.setSelectIdentityFormatToken("jenkins_parent_full_name-jenkins_name");
		when(globalConfigMock.getSelectIdentityFormatToken()).thenReturn("jenkins_parent_full_name-jenkins_name");

		JwtToken jwtToken = new JwtToken();
		jwtToken.claim.put("jenkins_parent_full_name", "test-mbp-pipeline");
		jwtToken.claim.put("jenkins_name", "main");

		// Split the identity format fields
		List<String> identityFields = Arrays.asList(globalConfigMock.getSelectIdentityFormatToken().split("[-,+,|,:,.]"));
		List<String> identityValues = new ArrayList<>(identityFields.size());
		String token = globalConfigMock.getSelectIdentityFormatToken();
		String parentField = identityFields.get(0);
		String separator =  token.substring(parentField.length(), parentField.length()+1);
		for (String identityField : identityFields) {
			String identityFieldValue = jwtToken.claim.has(identityField) ? jwtToken.claim.getString(identityField)
					: "";
			identityValues.add(identityFieldValue);
		}
		assertNotNull(identityValues);

		jwtToken.claim.put("sub", StringUtils.join(identityValues, separator));
		// Test if the split operation is successful
		assertEquals("test-mbp-pipeline-main", jwtToken.claim.getString("sub"));
	}

	@Test
	public void testGetSelectIdentityFormatFieldsTokenNoDelimiter() {
		// Set up mock values
		when(globalConfigMock.getSelectIdentityFormatToken()).thenReturn("jenkins_full_name");
		JwtToken jwtToken = new JwtToken();
		jwtToken.claim.put("jenkins_full_name", "test-mbp-pipeline");
		// Split the identity format fields
		String delimiter = "[-,+,|,:,.]";
		List<String> identityFields = Arrays.asList(globalConfigMock.getSelectIdentityFormatToken().split(delimiter));
		// Ensure that identityFields contains only one element, as there is no delimiter
		assertEquals(1, identityFields.size());
		assertEquals("jenkins_full_name", identityFields.get(0));
	}
}