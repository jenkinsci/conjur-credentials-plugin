package org.conjur.jenkins.configuration;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.ModelObject;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example of Jenkins global configuration.
 */
@Extension
public class GlobalConjurConfiguration extends GlobalConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    private ConjurConfiguration conjurConfiguration;
    private String authWebServiceId = "";
    private String jwtAudience = "cyberark-conjur";
    private long keyLifetimeInMinutes = 60;
    private long tokenDurationInSeconds = 120;
    private String selectAuthenticator = "APIKey";
    private Boolean enableIdentityFormatFieldsFromToken = false;
    private String identityFormatFieldsFromToken = "jenkins_full_name";
    private String selectIdentityFormatToken = "jenkins_full_name";
    private String selectIdentityFieldsSeparator = "-";
    private String identityFieldName = "sub";

    private static final Logger LOGGER = Logger.getLogger(GlobalConjurConfiguration.class.getName());

    /**
     * check the Auth WebService Id
     *
     * @param anc              AbstractItem
     * @param authWebServiceId Token
     * @return FormValidation - information about form status
     */
    public FormValidation doCheckAuthWebServiceId(@AncestorInPath AbstractItem anc,
                                                  @QueryParameter("authWebServiceId") String authWebServiceId) {
        if (StringUtils.isEmpty(authWebServiceId) || StringUtils.isBlank(authWebServiceId)) {
            LOGGER.log(Level.FINEST, "Auth WebService Id should not be empty");
            return FormValidation.error("Auth WebService Id should not be empty");
        } else {
            return FormValidation.ok();
        }
    }

    /**
     * @return the singleton instance , comment non-null due to trace exception
     */
    public static GlobalConjurConfiguration get() {
        GlobalConjurConfiguration result = null;
        try {
            result = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);

            if (result == null) {
                throw new IllegalStateException();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve GlobalConjurConfiguration", ex);
        }
        return result;
    }

    /**
     * When Jenkins is restarted, load any saved configuration from disk.
     */
    public GlobalConjurConfiguration() {
        LOGGER.log(Level.FINEST, "GlobalConjurConfiguration load()");
        // When Jenkins is restarted, load any saved configuration from disk.
        load();
    }

    /**
     * @return ConjurConfiguration
     */
    public ConjurConfiguration getConjurConfiguration() {
        return conjurConfiguration;
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
     * @return the JWT Audience
     */
    public String getJwtAudience() {
        return jwtAudience;
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
     **/
    public long getTokenDurationInSeconds() {
        return tokenDurationInSeconds;
    }

    /**
     * set the Token duration in seconds
     **/
    @DataBoundSetter
    public void setTokenDurationInSeconds(long tokenDurationInSeconds) {
        this.tokenDurationInSeconds = tokenDurationInSeconds;
        save();
    }

    /**
     * set the Conjur Configuration parameters
     **/
    @DataBoundSetter
    public void setConjurConfiguration(ConjurConfiguration conjurConfiguration) {
        this.conjurConfiguration = conjurConfiguration;
        save();
    }

    /**
     * @return selected authenticator name
     */
    public String getSelectAuthenticator() {
        return selectAuthenticator;
    }

    /**
     * @param authenticator name of authenticator
     */
    @DataBoundSetter
    public void setSelectAuthenticator(String authenticator) {
        LOGGER.log(Level.FINEST, String.format("GlobalConjurConfiguration authenticator set to: %s", authenticator));
        this.selectAuthenticator = authenticator;
        save();
    }

    /**
     * POST method to obtain the JWTtoken for the Item
     *
     * @param item Jenkins Item
     * @return status ok based on the FormValidation
     */
    @POST
    public FormValidation doObtainJwtToken(@AncestorInPath ModelObject item) {
        GlobalConjurConfiguration globalConfig = GlobalConfiguration.all().get(GlobalConjurConfiguration.class);
        // global context is when item is equal to null
        if (item == null) {
            item = Jenkins.get();
        }

        JwtToken token = JwtToken.getUnsignedToken("pluginAction", item, globalConfig);
        if (token != null) {
            return FormValidation.ok("JWT Token: \n" + token.claim.toString(4));
        }
        return FormValidation.ok("JWT Token: \nCannot obtain token");
    }

    public Boolean getEnableIdentityFormatFieldsFromToken() {
        return enableIdentityFormatFieldsFromToken;
    }

    @DataBoundSetter
    public void setEnableIdentityFormatFieldsFromToken(Boolean enableIdentityFormatFieldsFromToken) {
        LOGGER.log(Level.WARNING, "DEPRECATED: GlobalConjurConfiguration get() #enableIdentityFormatFieldsFromToken " + enableIdentityFormatFieldsFromToken);
        this.enableIdentityFormatFieldsFromToken = enableIdentityFormatFieldsFromToken;
        save();
    }

    public String getSelectIdentityFormatToken() {
        return selectIdentityFormatToken;
    }

    @DataBoundSetter
    public void setSelectIdentityFormatToken(String selectIdentityFormatToken) {
        LOGGER.log(Level.FINEST, "GlobalConjurConfiguration get() #selectIdentityFormatToken " + selectIdentityFormatToken);
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

    public String getidentityFieldName() {
        return identityFieldName;
    }

    @DataBoundSetter
    public void setIdentityFieldName(String identityFieldName) {
        this.identityFieldName = (!identityFieldName.isEmpty()) ? identityFieldName : "sub";
        save();
    }

    public String getIdentityFormatFieldsFromToken() {
        return identityFormatFieldsFromToken;
    }

    @DataBoundSetter
    public void setIdentityFormatFieldsFromToken(String identityFormatFieldsFromToken) {
        LOGGER.log(Level.FINE, "GlobalConjurConfiguration get() #identityFormatFieldsFromToken " + identityFormatFieldsFromToken);
        this.identityFormatFieldsFromToken = identityFormatFieldsFromToken;
        save();
    }

}
