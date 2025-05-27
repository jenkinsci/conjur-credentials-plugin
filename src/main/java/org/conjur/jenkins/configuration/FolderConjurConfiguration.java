package org.conjur.jenkins.configuration;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;

import hudson.Extension;
import hudson.model.Item;
import jenkins.model.Jenkins;

/**
 * Class to hold the Folder level Conjur Configuration
 */
public class FolderConjurConfiguration extends AbstractFolderProperty<AbstractFolder<?>> {

	private ConjurConfiguration conjurConfiguration;

	/** Constructor to set the Folder level configuration to ConjurConfiguration */
	@DataBoundConstructor
	public FolderConjurConfiguration(ConjurConfiguration conjurConfiguration) {
		super();
		this.conjurConfiguration = conjurConfiguration;
	}

	/**
	 *  @return ConjurConfiguration
	 **/
	public ConjurConfiguration getConjurConfiguration() {
		return conjurConfiguration;
	}

	/**
	 *
	 * @param conjurConfiguration folder configuration
	 **/
	@DataBoundSetter
	public void setConjurConfiguration(ConjurConfiguration conjurConfiguration) {
		this.conjurConfiguration = conjurConfiguration;
	}

	/**
	 * @return true if inheritedFromParent
	 **/
	public Boolean getInheritFromParent() {
		if( this.conjurConfiguration.getInheritFromParent() == null ) return Boolean.TRUE;
		return conjurConfiguration.getInheritFromParent();
	}

	/**
	 * set the boolean value based on inheritedFromParent checkbox
	 * @param inheritFromParent boolean option, it enable or disable inheritance
	 **/
	@DataBoundSetter
	public void setInheritFromParent(Boolean inheritFromParent) {
		this.conjurConfiguration.setInheritFromParent(inheritFromParent);
	}

	@Extension
	public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {
	}

	/**
	 * @return the Jenkins Item object baseon ownerFullName
	 **/
	public Item getItem() {
		return Jenkins.get().getItemByFullName(this.owner.getFullName());
	}
}
