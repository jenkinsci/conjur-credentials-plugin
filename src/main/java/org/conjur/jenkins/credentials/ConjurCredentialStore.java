package org.conjur.jenkins.credentials;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.acegisecurity.Authentication;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconType;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.export.ExportedBean;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.CredentialsStoreAction;
import com.cloudbees.plugins.credentials.domains.Domain;

import hudson.model.Item;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.security.Permission;
import jenkins.model.Jenkins;

/**
 * Retrieve the Credential Store details for Conjur Configuration
 *
 */
public class ConjurCredentialStore extends CredentialsStore {

	private static final Logger LOGGER = Logger.getLogger(ConjurCredentialStore.class.getName());
	private static ConcurrentHashMap<String, ConjurCredentialStore> allStores = new ConcurrentHashMap<String, ConjurCredentialStore>();
	private final ConjurCredentialProvider provider;
	private final ModelObject context;
	private final ConjurCredentialStoreAction action;

	public ConjurCredentialStore(ConjurCredentialProvider provider, ModelObject context) {
		super(ConjurCredentialProvider.class);
		this.provider = provider;
		this.context = context;
		this.action = new ConjurCredentialStoreAction(this, context);
	}

	public static ConcurrentMap<String, ConjurCredentialStore> getAllStores() {
		return allStores;
	}

	/**
	 * @return the Context as ModelObject
	 */

	@Nonnull
	@Override
	public ModelObject getContext() {
		return this.context;
	}

	/**
	 * 
	 * Checks if the given authentication has the specified permission.
	 * This includes checking if the user is an admin, has global credentials view permission or has Jenkins current item permissions.
	 * 
	 * @param authentication the authentication object representing the current user/system.
	 * @param permission     the specific permission to be checked.
	 * 
	 * @return true if the user is admin, has global credentials view permissions, or has Jenkins current item permissions, false otherwise..
	 */
	@Override
	public boolean hasPermission(@Nonnull Authentication authentication, @Nonnull Permission permission) {
		LOGGER.log(Level.FINE, "***** Conjur CredentialStore hasPermission() ");
		// Check if the user has global admin permission
		boolean isAdmin = Jenkins.get().getACL().hasPermission2(authentication.toSpring(), Jenkins.ADMINISTER);
		boolean hasCredentialsView = Jenkins.get().getACL().hasPermission2(authentication.toSpring(),
				CredentialsProvider.VIEW);

		LOGGER.log(Level.FINE, "Checking permissions for the user: " + authentication.getName());
		LOGGER.log(Level.FINE, "Admin permission: " + isAdmin);
		LOGGER.log(Level.FINE, "Credentials view permission: " + hasCredentialsView);
		
		//If the permission being checked is not VIEW, return false immediately
		if(!CredentialsProvider.VIEW.equals(permission)) {
			return false;	
		}
		//If non-admin don't have permission to view global credentials
		if(!hasCredentialsView && !isAdmin) {
			//Get the current item from the context
			Item currentItem = Stapler.getCurrentRequest().findAncestorObject(Item.class);
			if(currentItem == null) {
				LOGGER.log(Level.WARNING, "Unable to determine the current item for permission check ");
				return false;
			}
			LOGGER.log(Level.FINE, "Current item: " + currentItem.getFullName());
			//If the user has credentials view permission at the Jenkins current item
			boolean hasItemViewPermission = currentItem.getACL().hasPermission2(authentication.toSpring(), CredentialsProvider.VIEW);
			LOGGER.log(Level.FINE, "Non-admin user for the current Jenkins item: " + currentItem.getFullName() + " - " + hasItemViewPermission);
			return hasItemViewPermission;
		}
		//Return true if the user is either an admin or has credentials view permission
		return isAdmin || hasCredentialsView;
	}

	/**
	 * @return List of Credentials to view based on permission
	 */
	@Nonnull
	@Override
	public List<Credentials> getCredentials(@Nonnull Domain domain) {
		LOGGER.log(Level.FINE, "***** Conjur CredentialStore getCredentials() ");
		Authentication authentication =Jenkins.getAuthentication();
		// If the user doesn't have permission, return an empty list
		if(!hasPermission(authentication, CredentialsProvider.VIEW)) {
			LOGGER.log(Level.FINE, "User: " + authentication.getName() + " does not have permission to view credentials.");
			return Collections.emptyList();
		}
		// Only the global domain is supported
		if (Domain.global().equals(domain)) {
			return provider.getCredentials(Credentials.class, Jenkins.get(), ACL.SYSTEM);
		} else {
			return Collections.emptyList();
		}
	}

	@Override
	public boolean addCredentials(@Nonnull Domain domain, @Nonnull Credentials credentials) {
		throw new UnsupportedOperationException("Jenkins may not add credentials to Conjur");
	}

	@Override
	public boolean removeCredentials(@Nonnull Domain domain, @Nonnull Credentials credentials) {
		throw new UnsupportedOperationException("Jenkins may not remove credentials from Conjur");
	}

	@Override
	public boolean updateCredentials(@Nonnull Domain domain, @Nonnull Credentials current,
			@Nonnull Credentials replacement) {
		throw new UnsupportedOperationException("Jenkins may not update credentials in Conjur");
	}

	@Nullable
	@Override
	public CredentialsStoreAction getStoreAction() {
		return action;
	}

    /**
     * Expose the store.
     */
    @ExportedBean
    public static class ConjurCredentialStoreAction extends CredentialsStoreAction {

        private static final String ICON_CLASS = "icon-conjur-credentials-store";

        private final ConjurCredentialStore store;

        private ConjurCredentialStoreAction(ConjurCredentialStore store, ModelObject context) {
            this.store = store;
            addIcons();
        }

        private void addIcons() {
            IconSet.icons.addIcon(new Icon(ICON_CLASS + " icon-sm",
                    "conjur-credentials/images/conjur-credential-store-sm.png",
                    Icon.ICON_SMALL_STYLE, IconType.PLUGIN));
            IconSet.icons.addIcon(new Icon(ICON_CLASS + " icon-md",
                    "conjur-credentials/images/conjur-credential-store-md.png",
                    Icon.ICON_MEDIUM_STYLE, IconType.PLUGIN));
            IconSet.icons.addIcon(new Icon(ICON_CLASS + " icon-lg",
                    "conjur-credentials/images/conjur-credential-store-lg.png",
                    Icon.ICON_LARGE_STYLE, IconType.PLUGIN));
            IconSet.icons.addIcon(new Icon(ICON_CLASS + " icon-xlg",
                    "conjur-credentials/images/conjur-credential-store-xlg.png",
                    Icon.ICON_XLARGE_STYLE, IconType.PLUGIN));
        }

        @Override
        @Nonnull
        public ConjurCredentialStore getStore() {
            return store;
        }

        @Override
        public String getIconFileName() {
            return isVisible()
                    ? "/plugin/conjur-credentials/images/conjur-credential-store-lg.png"
                    : null;
        }

        @Override
        public String getIconClassName() {
            return isVisible()
                    ? ICON_CLASS
                    : null;
        }

        @Override
        public String getDisplayName() {
            return "Conjur Credential Store" ;
        }
    }
}