package org.conjur.jenkins.configuration;


import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * ConjurJITJobProperty DTO class to set the ConjurConfiguration
 *
 *  extends Jenkins JobProperty
 */
public class ConjurJITJobProperty<T extends Job<?, ?>> extends JobProperty<T> {

    private static final String DISPLAY_NAME = "Conjur Just-In-Time Access";

    // Configured WebServiceId for authentication
    private String authWebServiceId = "";

    // HostPrefix
    private String hostPrefix = "";

    // ConjurConfiguration parameter
    private ConjurConfiguration conjurConfiguration;

	/**
	 * Constructor to set the ConjurConfiguration object
 	 */
    @DataBoundConstructor
    public ConjurJITJobProperty(ConjurConfiguration conjurConfiguration) {
        super();
        this.conjurConfiguration = conjurConfiguration;
    }

	/**
	 * @return the ConjurConfiguration
	 */
	public ConjurConfiguration getConjurConfiguration() {
        return conjurConfiguration;
    }

    /**
     * Together with {@link #getConjurConfiguration}, binds to entry in
     * {@code config.jelly}.
     *
     * @param conjurConfiguration the new value of Conjur Configuration
    **/
    @DataBoundSetter
    public void setConjurConfiguration(ConjurConfiguration conjurConfiguration) {
        this.conjurConfiguration = conjurConfiguration;
    }

	/**
	 *
	 * @return the Parent Configuration
	 */
    public Boolean getInheritFromParent() {
        if (this.conjurConfiguration.getInheritFromParent() == null) return Boolean.TRUE;
        return this.conjurConfiguration.getInheritFromParent();
    }

    /**
     * Together with {@link #getInheritFromParent}, binds to entry in {@code config.jelly}.
     *
     * @param inheritFromParent true if inherited from parent configuration
     */
    @DataBoundSetter
    public void setInheritFromParent(Boolean inheritFromParent) {
        this.conjurConfiguration.setInheritFromParent(inheritFromParent);
    }

    /**
     *
     * @return the AuthenticationWebServiceId
     */
    public String getAuthWebServiceId() {
        return authWebServiceId;
    }
    /**
     * Together with {@link #getAuthWebServiceId}, binds to entry in
     * {@code config.jelly}.
     *
     * @param authWebServiceId the Webservice Id
     */
    @DataBoundSetter
    public void setAuthWebServiceId(String authWebServiceId) {
        this.authWebServiceId = authWebServiceId;
    }

    /**
     *
     * @return the host prefix
     */
    public String getHostPrefix() {
        return hostPrefix;
    }

    /**
     *
     * @return the Item
     */
    public Item getItem() {
        return Jenkins.get().getItemByFullName(this.owner.getFullName());
    }

    /**
     *
     * @param hostPrefix the HostPrefix Id
     */
    @DataBoundSetter
    public void setHostPrefix(String hostPrefix) {
        this.hostPrefix = hostPrefix;
    }

    /**
     * Inner static class to retrieve the display name and returns the property for
     * all job types
     */
    @Extension
    public static final class ConjurJITJobPropertyDescriptorImpl extends JobPropertyDescriptor {

        /**
         * Getter function for a human readable class display name.
         *
         * @return a String containing the human readable display name for the
         * {@link JobProperty} class.
         */
        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        /**
         Indicates where this property can be used

         @param jobType - a Job object
         @return Always true. This property can be set for all Job types.
        **/
        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }
    }
}
