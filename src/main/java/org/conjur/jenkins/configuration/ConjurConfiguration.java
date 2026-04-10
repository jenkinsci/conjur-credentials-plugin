package org.conjur.jenkins.configuration;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.conjur.jenkins.jwtauth.impl.JwtToken;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.io.Serializable;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ConjurConfiguration class extends Jenkins AbstractDescribableImpl class and
 * implements Serializable Retrieves the Conjur configuration details and assign
 * to Configuration parameters
 */
public class ConjurConfiguration extends AbstractDescribableImpl<ConjurConfiguration> implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(ConjurConfiguration.class.getName());

    /**
     * Internal data
     */
    private Boolean inheritFromParent = Boolean.TRUE;
    private static final long serialVersionUID = 1L;
    private String applianceURL;
    private String account;
    private String credentialID;
    private String certificateCredentialID;
    private CertificateCredentials certificateCredentials;
    private String ownerFullName;

    public ConjurConfiguration() {
    }

    /**
     * Inner static class to retrieve the configuration details from Jenkins
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<ConjurConfiguration> {
        /**
         * Retrieve the conjur credentials and populate back to the ListBox based on the
         * CertificateCredentialIDItems.
         *
         * @param item          Jenkins  Item Object for the pipeline
         * @param credentialsId id of credentials
         * @return Jenkins ListBoxModel
         */
        public ListBoxModel doFillCertificateCredentialIDItems(@AncestorInPath Item item,
                                                               @QueryParameter String credentialsId) {
            return fillCredentialIDItemsWithClass(item, credentialsId, StandardCertificateCredentials.class);
        }

        /**
         * Retrieve the conjur credentials and populate back to the ListBox based on the
         * CredentialIDItems.
         *
         * @param item          Jenkins Item Object for the pipeline
         * @param credentialsId id of credentials
         * @return Jenkins ListBoxModel
         */

        public ListBoxModel doFillCredentialIDItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            return fillCredentialIDItemsWithClass(item, credentialsId, StandardUsernamePasswordCredentials.class);
        }

        /**
         * Overriden method to display name
         *
         * @return the name to be displayed
         */
        @Override
        public String getDisplayName() {
            return "Conjur Configuration";
        }

        /**
         * POST method to obtain the JWTtoken for the Item
         *
         * @param item Object for which JWTToken will be generated
         * @return status ok based on the FormValidation
         */
        @POST
        public FormValidation doObtainJwtToken(@AncestorInPath Item item) {
            //Obtain the global Conjur configuration object
            GlobalConjurConfiguration globalConfig = GlobalConjurConfiguration.get();
            // Call the getToken method to obtain the JWT token
            JwtToken token = JwtToken.getUnsignedToken("pluginAction", item, globalConfig);
            return FormValidation.ok("JWT Token: \n" + token.claim.toString(4));
        }
    }

    /**
     * DataBoundConstructor to bind the configuration
     *
     * @param applianceURL Conjur url
     * @param account      host
     */
    @DataBoundConstructor
    public ConjurConfiguration(String applianceURL, String account) {
        if (applianceURL.endsWith("/")) {
            // Remove trailing slash from appliance URL
            this.applianceURL = applianceURL.substring(0, applianceURL.length() - 1);
        } else {
            this.applianceURL = applianceURL;
        }
        this.account = account;
    }

    /**
     * @return the currently configured Account, if any
     */
    public String getAccount() {
        return account;
    }

    /**
     * @return the currently appliance URL, if any
     */
    public String getApplianceURL() {
        return applianceURL;
    }

    /**
     * @return the currently certification credentail Id, if any
     */
    public String getCertificateCredentialID() {
        return certificateCredentialID;
    }

    /**
     * @return the currently certification credentails, if any
     */
    public CertificateCredentials getCertificateCredentials() {
        return certificateCredentials;
    }

    /**
     * @return the currently credentail Id, if any
     */
    public String getCredentialID() {
        return credentialID;
    }


    /**
     * @return the currently Owner full name, if any
     */
    public String getOwnerFullName() {
        return ownerFullName;
    }

    /**
     * Retrieve information about choosen authenticator
     *
     * @return authenticator set as string
     */

    public String getGlobalAuthenticator() {
        return GlobalConjurConfiguration.get().getSelectAuthenticator();
    }

    @DataBoundSetter
    public void setGlobalAuthenticator(String globalAuthenticator) {
        // we use this field only to get global configuration
    }

    /**
     * Together with {@link #getAccount}, binds to entry in {@code config.jelly}.
     *
     * @param account the new value of Conjur account
     */
    @DataBoundSetter
    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * Together with {@link #getApplianceURL}, binds to entry in
     * {@code config.jelly}.
     *
     * @param applianceURL the new value of Conjur Appliance URL
     */
    @DataBoundSetter
    public void setApplianceURL(String applianceURL) {
        this.applianceURL = applianceURL;
    }

    /**
     * Together with {@link #getCertificateCredentialID}, binds to entry in
     * {@code config.jelly}.
     *
     * @param certificateCredentialID the new value of Conjur
     *                                CertificateCredentialID
     */
    @DataBoundSetter
    public void setCertificateCredentialID(String certificateCredentialID) {
        this.certificateCredentialID = certificateCredentialID;

        if (certificateCredentialID == null) {
            LOGGER.log(Level.FINEST, "CertificationID is null");
            return;
        }

        // we have to be aware, this function is calling getCredentials in provider
        this.certificateCredentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(CertificateCredentials.class, Jenkins.get(), ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(certificateCredentialID));
    }

    /**
     * @param certificateCredentials the new value of Conjur
     *                               CertificateCredential
     */
    @DataBoundSetter
    public void setCertificateCredentials(CertificateCredentials certificateCredentials) {
        this.certificateCredentials = certificateCredentials;
    }

    /**
     * Together with {@link #getCredentialID}, binds to entry in
     * {@code config.jelly}.
     *
     * @param credentialID the new value of Conjur credentialID
     */
    @DataBoundSetter
    public void setCredentialID(String credentialID) {
        this.credentialID = credentialID;
    }

    /**
     * Together with {@link #getOwnerFullName}, binds to entry in
     * {@code config.jelly}.
     *
     * @param ownerFullName the new value of Conjur OwnerFullname
     */
    public void setOwnerFullName(String ownerFullName) {
        this.ownerFullName = ownerFullName;
    }

    /**
     * @param item            Jenkins item
     * @param credentialsId   id of credentials
     * @param credentialClass class type of credentials
     * @return list which contain all credentials with specified id and class
     */
    private static ListBoxModel fillCredentialIDItemsWithClass(Item item, String credentialsId, Class<? extends StandardCredentials> credentialClass) {
        StandardListBoxModel result = new StandardListBoxModel();
        if (item == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return result.includeCurrentValue(credentialsId);
        }

        if (item != null
                && !item.hasPermission(Item.EXTENDED_READ)
                && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
            return result.includeCurrentValue(credentialsId);
        }

        return result
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM, item, credentialClass, URIRequirementBuilder.fromUri(credentialsId).build())
                .includeCurrentValue(credentialsId);
    }

    /**
     * Get information if configuration and secrets should be inherited from parent object
     *
     * @return information if entry can inherit data from parent object
     */
    public Boolean getInheritFromParent() {
        return inheritFromParent;
    }

    /**
     * Together with {@link #getInheritFromParent}, binds to entry in
     * {@code config.jelly}.
     *
     * @param inheritFromParent true if inherited from parent configuration
     */
    @DataBoundSetter
    public void setInheritFromParent(Boolean inheritFromParent) {
        if (inheritFromParent == null) {
            this.inheritFromParent = Boolean.TRUE;
        }
        this.inheritFromParent = inheritFromParent;
    }

    /**
     * @param config Create copy of ConjurConfiguration
     */
    public ConjurConfiguration(ConjurConfiguration config) {
        this.credentialID = config.getCredentialID();
        this.account = config.getAccount();
        this.ownerFullName = config.getOwnerFullName();
        this.certificateCredentialID = config.getCertificateCredentialID();
        this.applianceURL = config.getApplianceURL();
    }

    /**
     * @param parent ConjurConfiguration which will be merged to current configuration
     * @return ConjurConfiguration
     */
    public ConjurConfiguration mergeWithParent(ConjurConfiguration parent) {
        if (parent == null) {
            return this;
        }
        ConjurConfiguration result = new ConjurConfiguration(this);

        if (StringUtils.isBlank(result.getAccount())) {
            result.setAccount(parent.getAccount());
        }
        if (StringUtils.isBlank(result.getOwnerFullName())) {
            result.setOwnerFullName(parent.getOwnerFullName());
        }
        if (StringUtils.isBlank(result.getCertificateCredentialID())) {
            result.setCertificateCredentialID(parent.getCertificateCredentialID());
        }
        if (StringUtils.isBlank(result.getCredentialID())) {
            result.setCredentialID(parent.getCredentialID());
        }
        if (result.certificateCredentials == null) {
            result.setCertificateCredentials(parent.getCertificateCredentials());
        }
        if (StringUtils.isBlank(result.getApplianceURL())) {
            result.setApplianceURL(parent.getApplianceURL());
        }
        return result;
    }
}
