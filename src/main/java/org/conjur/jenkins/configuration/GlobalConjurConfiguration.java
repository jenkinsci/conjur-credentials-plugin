package org.conjur.jenkins.configuration;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;

/**
 * Example of Jenkins global configuration.
 */
@Extension
public class GlobalConjurConfiguration extends GlobalConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    private ConjurConfiguration conjurConfiguration;
    private Boolean enableJWKS = false;
    private String authWebServiceId = "";
    private String jwtAudience = "cyberark-conjur";
    private long keyLifetimeInMinutes = 60;
    private long tokenDurarionInSeconds = 120;
    private Boolean enableContextAwareCredentialStore = false;

    private Boolean enableIdentityFormatFieldsFromToken = false;

    private String identityFormatFieldsFromToken = "jenkins_full_name";

    private  String  selectIdentityFormatToken = "jenkins_full_name";
 //   private String identityFieldsSeparator = "-";

    private String selectIdentityFieldsSeparator = "-";
    private String identityFieldName = "sub";

    private static final Logger LOGGER = Logger.getLogger(GlobalConjurConfiguration.class.getName());

    /**
     * check the Token Duration for validity
     *
     * @param Jenkins AbstractItem anc
     * @param Token   duration in sectonds
     * @param Token   keyLifetimeInMinutes
     * @return
     */
    public FormValidation doCheckTokenDurarionInSeconds(@AncestorInPath AbstractItem anc,
                                                        @QueryParameter("tokenDurarionInSeconds") String tokenDurarionInSeconds,
                                                        @QueryParameter("keyLifetimeInMinutes") String keyLifetimeInMinutes) {
        LOGGER.log(Level.FINE, "Inside of doCheckTokenDurarionInSeconds()");
        try {
            int tokenttl = Integer.parseInt(tokenDurarionInSeconds);
            int keyttl = Integer.parseInt(keyLifetimeInMinutes);
            if (tokenttl > keyttl * 60) {
                LOGGER.log(Level.FINE, "Token cannot last longer than key");
                return FormValidation.error("Token cannot last longer than key");
            } else {
                return FormValidation.ok();
            }
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Key lifetime and token duration must be numbers");
            return FormValidation.error("Key lifetime and token duration must be numbers");
        }
    }

    /**
     * check the Auth WebService Id
     *
     * @param Jenkins AbstractItem anc
     * @param Token   authWebServiceId
     * @return
     */
    public FormValidation doCheckAuthWebServiceId(@AncestorInPath AbstractItem anc,
                                                  @QueryParameter("authWebServiceId") String authWebServiceId) {
        LOGGER.log(Level.FINE, "Inside of doCheckAuthWebServiceId()");
        if (StringUtils.isEmpty(authWebServiceId) || StringUtils.isBlank(authWebServiceId)) {
            LOGGER.log(Level.FINE, "Auth WebService Id should not be empty");
            return FormValidation.error("Auth WebService Id should not be empty");
        }else {
            return FormValidation.ok();
        }
    }

    /**
     * check the JWT Audience
     *
     * @param Jenkins AbstractItem anc
     * @param jwtAudience
     * @return
     */
    public FormValidation doCheckJwtAudience(@AncestorInPath AbstractItem anc,
                                             @QueryParameter("jwtAudience") String jwtAudience) {
        LOGGER.log(Level.FINE, "Inside of doCheckJwtAudience()");
        if (StringUtils.isEmpty(jwtAudience) || StringUtils.isBlank(jwtAudience)) {
            LOGGER.log(Level.FINE, "JWT Audience field value defaults to: cyberark-conjur");
            return FormValidation.warning("JWT Audience field value defaults to: cyberark-conjur ");
        } else {
            return FormValidation.ok();

        }
    }
	/**
	 * check the Identity field Name
	 *
	 * @param Jenkins AbstractItem anc
	 * @param identityFieldName
	 * @return
	 */
    public FormValidation doCheckIdentityFieldName(@AncestorInPath AbstractItem anc,
                                                   @QueryParameter("identityFieldName") String identityFieldName) {
        // Regular expression to allow only alphanumeric characters
        String alphanumericRegex = "^[a-zA-Z0-9\\-_\"]*$";
		if (StringUtils.isEmpty(identityFieldName) || StringUtils.isBlank(identityFieldName)) {
			LOGGER.log(Level.FINE, "Identity Field Name should not be empty");
			return FormValidation.error("Identity Field Name should not be empty");
		}if (!identityFieldName.matches(alphanumericRegex)) {
            LOGGER.log(Level.FINE, "Identity Field Name should contain only alphanumeric characters including \"-\", \"_\", and \" \"");
            return FormValidation.error("Identity Field Name should contain only alphanumeric characters including \"-\", \"_\", and \" \"");
        }
		return FormValidation.ok();
    }

    /**
     * check the Identity Format Fields From Token
     *
     * @param Jenkins AbstractItem anc
     * @param Token   identityFormatFieldsFromToken
     * @return
     */
    public FormValidation doCheckIdentityFormatFieldsFromToken(@AncestorInPath AbstractItem anc,
                                                               @QueryParameter("identityFormatFieldsFromToken") String identityFormatFieldsFromToken) {
        LOGGER.log(Level.FINE, "Inside of doCheckIdentityFormatField()");
        List<String> identityFields = Arrays.asList(identityFormatFieldsFromToken.split(","));
        if (StringUtils.isEmpty(identityFormatFieldsFromToken) || StringUtils.isBlank(identityFormatFieldsFromToken)) {
            LOGGER.log(Level.FINE, "Identity Format Fields should not be empty");
            return FormValidation.error("Identity Format Fields should not be empty");
        }
        return validateIdentityFormatFields(identityFields);
    }

  
    private FormValidation validateIdentityFormatFields(List<String> identityFields) {
        // Check for the presence of either jenkins_full_name or the combination of jenkins_parent_full_name and jenkins_name
        boolean jenkinsFullNameExists = identityFields.contains("jenkins_full_name");
        boolean jenkinsParentFullNameExists = identityFields.contains("jenkins_parent_full_name");
        boolean jenkinsNameExists = identityFields.contains("jenkins_name");
        
        if (jenkinsFullNameExists) {
            // No validation errors
            return FormValidation.ok();
        }else if (jenkinsParentFullNameExists && jenkinsNameExists)  {
            // Only jenkins_full_name exists
            return FormValidation.ok();
        } else{
            // Neither jenkins_full_name nor valid combination found
            return handleValidationError("jenkins_full_name or a combination of jenkins_parent_full_name and jenkins_name");
        }
    }

    private FormValidation handleValidationError(String tokens) {
        LOGGER.log(Level.WARNING, "Identity Format Fields must contain at least one of the  " + tokens);
        return FormValidation.error("Identity Format Fields must contain at least one of the " + tokens);
    }

    /**
     * @return the singleton instance , comment non-null due to trace exception
     */
    // @Nonnull
    public static GlobalConjurConfiguration get() {
        LOGGER.log(Level.FINE, "GlobalConjurConfiguration get()");
        GlobalConjurConfiguration result = null;
        try {
            result = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);

            LOGGER.log(Level.FINE, "Inside GlobalConjurConfiguration get() result:  " + result);

            if (result == null) {
                throw new IllegalStateException();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return result;
    }

    /**
     * When Jenkins is restarted, load any saved configuration from disk.
     */

    public GlobalConjurConfiguration() {
        LOGGER.log(Level.FINE, "GlobalConjurConfiguration load()");
        // When Jenkins is restarted, load any saved configuration from disk.
        load();
    }

    /**
     * @return ConjurConfiguration object
     */
    public ConjurConfiguration getConjurConfiguration() {
        return conjurConfiguration;
    }

    /**
     * @return boolean if JWKS is enabled
     */
    public Boolean getEnableJWKS() {
        return enableJWKS;
    }

    /**
     * @return boolean for enableContextAware CredentialStore
     */

    public Boolean getEnableContextAwareCredentialStore() {
        return enableContextAwareCredentialStore;
    }

    /**
     * @return Web Service ID for authentication
     */
    public String getAuthWebServiceId() {
        return authWebServiceId;
    }

    /**
     * set the Authentication WebService Id
     */
    @DataBoundSetter
    public void setAuthWebServiceId(String authWebServiceId) {
        this.authWebServiceId = authWebServiceId;
        save();
    }

    /**
     * @return the Identity FieldName
     */
    public String getidentityFieldName() {
        return identityFieldName;
    }

    /**
     * set the IdentityFieldName
     */
    @DataBoundSetter
    public void setIdentityFieldName(String identityFieldName) {
        this.identityFieldName = (!identityFieldName.isEmpty()) ? identityFieldName : "sub";
        save();
    }

    /**
     * @retrun IdentityFormatFieldsFromToken
     */
    public String getIdentityFormatFieldsFromToken() {
        return identityFormatFieldsFromToken;
    }

    /**
     * set the IdentityFormatFieldsFromToken
     */
    @DataBoundSetter
    public void setIdentityFormatFieldsFromToken(String identityFormatFieldsFromToken) {
        LOGGER.log(Level.FINE, "GlobalConjurConfiguration get() #identityFormatFieldsFromToken " + identityFormatFieldsFromToken);
        this.identityFormatFieldsFromToken = identityFormatFieldsFromToken;
        save();
    }


  

    /**
     * @return the JWT Audience
     */
    public String getJwtAudience() {
        return jwtAudience;
    }

    /**
     * set the JWT Audience
     */

    @DataBoundSetter
    public void setJwtAudience(String jwtAudience) {
        this.jwtAudience = (!jwtAudience.isEmpty()) ? jwtAudience : "cyberark-conjur";
        save();
    }

    /**
     * @return the Key Life Time in Minutes
     */
    public long getKeyLifetimeInMinutes() {
        return keyLifetimeInMinutes;
    }

    /**
     * set the Key Life Time in Minutes
     */
    @DataBoundSetter
    public void setKeyLifetimeInMinutes(long keyLifetimeInMinutes) {
        this.keyLifetimeInMinutes = keyLifetimeInMinutes;
        save();
    }

    /**
     * @return the Token duration in seconds
     */
    public long getTokenDurarionInSeconds() {
        return tokenDurarionInSeconds;
    }

    /**
     * set the Token duration in seconds
     */
    @DataBoundSetter
    public void setTokenDurarionInSeconds(long tokenDurarionInSeconds) {
        this.tokenDurarionInSeconds = tokenDurarionInSeconds;
        save();
    }

    /**
     * set the Conjur Configuration parameters
     */
    @DataBoundSetter
    public void setConjurConfiguration(ConjurConfiguration conjurConfiguration) {
        this.conjurConfiguration = conjurConfiguration;
        save();
    }

    /**
     * set Enable JWKS option
     */
    @DataBoundSetter
    public void setEnableJWKS(Boolean enableJWKS) {
        this.enableJWKS = enableJWKS;
        save();
    }

    /**
     * set the EnablContextAwareCredentialStore selected value
     */
    @DataBoundSetter
    public void setEnableContextAwareCredentialStore(Boolean enableContextAwareCredentialStore) {
        this.enableContextAwareCredentialStore = enableContextAwareCredentialStore;
        save();
    }

    public Boolean getEnableIdentityFormatFieldsFromToken() {
        return enableIdentityFormatFieldsFromToken;
    }

    @DataBoundSetter
    public void setEnableIdentityFormatFieldsFromToken(Boolean enableIdentityFormatFieldsFromToken) {
        LOGGER.log(Level.FINE, "GlobalConjurConfiguration get() #enableIdentityFormatFieldsFromToken " + enableIdentityFormatFieldsFromToken);
        this.enableIdentityFormatFieldsFromToken = enableIdentityFormatFieldsFromToken;
        save();
    }

    public String getSelectIdentityFormatToken() {
        return selectIdentityFormatToken;
    }

    @DataBoundSetter
    public void setSelectIdentityFormatToken(String selectIdentityFormatToken) {
        LOGGER.log(Level.FINE, "GlobalConjurConfiguration get() #selectIdentityFormatToken " + selectIdentityFormatToken);
        this.selectIdentityFormatToken = selectIdentityFormatToken;
        save();
    }

    public String getSelectIdentityFieldsSeparator() {
        return selectIdentityFieldsSeparator;
    }

    @DataBoundSetter
    public void setSelectIdentityFieldsSeparator(String selectIdentityFieldsSeparator) {
        this.selectIdentityFieldsSeparator = selectIdentityFieldsSeparator;
        save();
    }
}
