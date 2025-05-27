package org.conjur.jenkins.credentials;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.CredentialsStoreAction;
import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentials;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconType;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.export.ExportedBean;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Retrieve the Credential Store details for Conjur Configuration
 *
 */
public class ConjurCredentialStore extends CredentialsStore {
	private static final String DISPLAY_NAME = "Conjur Credential Storage";
	private static final Logger LOGGER = Logger.getLogger(ConjurCredentialStore.class.getName());
	private static ConcurrentHashMap<String, ConjurCredentialStore> allStores = new ConcurrentHashMap<String, ConjurCredentialStore>();
	private final ConjurCredentialProvider provider;
	private final ModelObject context;
	private final ConjurCredentialStoreAction action;

	/**
	 * Constructor
	 * @param provider ConjurCredentialProvider responsible for delivering Credentials from Conjur
	 * @param context Context to which Store will be assigned
	 */
	public ConjurCredentialStore(ConjurCredentialProvider provider, ModelObject context) {
		super(ConjurCredentialProvider.class);

		this.provider = provider;
		this.context = context;
		this.action = new ConjurCredentialStoreAction(this, context);
	}

	/**
	 * Store ConjurCredentialStore
	 * @param key name to which ConjurCredentialStore will be assigned
	 * @param c ConjurCredentialStore
	 */
	public static void putCredentialStore(String key, ConjurCredentialStore c ){
		allStores.put( key, c );
	}

	/**
	 * Get ConjurCredentialStore
	 * @param key name used to get store
	 * @return ConjurCredentialStore if exist or null
	 */
	public static ConjurCredentialStore getCredentialStore(String key )
	{
		return allStores.get( key );
	}

	/**
	 * Check if ConjurCredentialStore exist
	 * @param key name used to get store
	 * @return true if store exist, otherwise false
	 */
	public static boolean isStoreContainsKey( String key )
	{
		return allStores.containsKey( key );
	}

	/**
	 * @return the Context as ModelObject
	 */
	@NonNull
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
	public boolean hasPermission2(@NonNull Authentication authentication, @NonNull Permission permission) {
		LOGGER.log(Level.FINEST, "Conjur CredentialStore hasPermission() ");
		// Check if the user has global admin permission
		boolean isAdmin = Jenkins.get().getACL().hasPermission2(authentication, Jenkins.ADMINISTER);
		boolean hasCredentialsView = Jenkins.get().getACL().hasPermission2(authentication, CredentialsProvider.VIEW);

		LOGGER.log(Level.FINEST,
				String.format("Checking permissions for the user: %s admin %s credview %s" ,
		 authentication.getName(),
						isAdmin?"yes":"no",
						hasCredentialsView?"yes":"no" ) );

		//If the permission being checked is not VIEW, return false immediately
		if(!CredentialsProvider.VIEW.equals(permission))
		{
			return false;
		}
		//If non-admin don't have permission to view global credentials
		if(!hasCredentialsView && !isAdmin)
		{
			//Get the current item from the context
			Item currentItem = Stapler.getCurrentRequest().findAncestorObject(Item.class);
			if(currentItem == null) {
				LOGGER.log(Level.WARNING, "Unable to determine the current item for permission check ");
				return false;
			}
			LOGGER.log(Level.FINEST, String.format("Current item: %s", currentItem.getFullName( ) ) );
			//If the user has credentials view permission at the Jenkins current item
			boolean hasItemViewPermission = currentItem.getACL().hasPermission2(authentication, CredentialsProvider.VIEW);
			LOGGER.log(Level.FINEST, String.format("Non-admin user for the current Jenkins item: %s - %s", currentItem.getFullName(), hasItemViewPermission?"yes":"no" ) );
			return hasItemViewPermission;	
		}
		//Return true if the user is either an admin or has credentials view permission
		return isAdmin || hasCredentialsView;
	}

	@Override
	public String getDisplayName()
	{
		return DISPLAY_NAME;
	}

	/**
	 * @return List of Credentials to view based on permission
	 */
	@NonNull
	@Override
	public List<Credentials> getCredentials(@NonNull Domain domain) {
		Authentication authentication = Jenkins.getAuthentication2();

		// If the user doesn't have permission, return an empty list
		if(!hasPermission2(authentication, CredentialsProvider.VIEW)) {
			LOGGER.log(Level.FINEST, String.format("User: %s does not have permission to view credentials.", authentication.getName() ) );
			return Collections.emptyList();
		}


		// if storage is global we have to return global credentials
		// current context from which call was done
		// context is context assigned to ConjurStorage
		Item currentContext = Stapler.getCurrentRequest().findAncestorObject(Item.class);

				// if we are on the top then we always return global credentials if avaiable
		if( context instanceof hudson.model.Hudson || currentContext == null )
		{
			LOGGER.log(Level.FINEST, "ConjurCredentialStore: Global credentials found!");

			return provider.getCredentials(Credentials.class, Jenkins.get());
		}

		//
		// Jenkins is always going through all folders to get secrets from storages
		// if we want to have similar functionality as in provider, we have to check which storage is checked and only
		// add credentials to display list which
		//

		String storePath = null;
		if( context instanceof Item )
		{
			Item contextItem = (Item) context;
			storePath = contextItem.getFullName();
		}else if( context instanceof ItemGroup )
		{
			ItemGroup<?> contextItem = (ItemGroup<?>) context;
			storePath = contextItem.getFullName();
		}

		// right now we support only Item and ItemGroup, they set storePath value
		if( storePath != null)
		{
			int pos = currentContext.getFullName().indexOf( storePath );

			// if its not subdirectory or entry below, quit
			if( pos != 0 )	// same path
			{
				LOGGER.log(Level.FINEST, "Cannot deliver credentials from path to which you don't have access"  );
				return Collections.emptyList();
			}
		}

		// Jenkins shows all storages, we have to check which one has access

		if( getContext() != currentContext )
		{
			// if inheritance is on for current context we can go through all directories
			if( ConjurAPI.isInheritanceOn( currentContext ) )
			{
				if( !(currentContext instanceof hudson.model.Hudson) ) {
					for (ItemGroup<?> currFolder = currentContext.getParent(); currFolder instanceof AbstractFolder; currFolder = (AbstractFolder.class.cast(currFolder)).getParent()) {
						Item folderAsItem = (Item)currFolder;

						LOGGER.log(Level.FINEST,
						String.format("ConjurCredentialStore getCredentials, context: "+
								" current folder name: %s"  +
								" currentContext.getFullDisplayName(): %s"+
								" g.getFullDisplayName(): %s "+
								" item: %s" , context.getDisplayName(),currFolder.getFullName(),
								currentContext.getFullName(), currFolder.getFullDisplayName(), folderAsItem.getFullName() ) );

						//
						// if its not subdirectory or inheritance is turned off
						//
						LOGGER.log(Level.FINEST, String.format("storepath %s actfolder %s",storePath, folderAsItem.getFullName() ) );

						boolean inheritanceIsOn = ConjurAPI.isInheritanceOn( currFolder );
						if( !inheritanceIsOn )	// same path
						{
							LOGGER.log(Level.FINEST, "Cannot deliver credentials from path to which you don't have access or inhertiance is: off" );
							return Collections.emptyList();
						}
					}
				}
			}
			else{
				return Collections.emptyList();
			}
		}
		return provider.getCredentials(Credentials.class, getContext());
	}

	@Override
	public boolean addCredentials(@NonNull Domain domain, @NonNull Credentials credentials) {
		throw new UnsupportedOperationException("Jenkins may not add credentials to Conjur");
	}

	@Override
	public boolean removeCredentials(@NonNull Domain domain, @NonNull Credentials credentials) {
		throw new UnsupportedOperationException("Jenkins may not remove credentials from Conjur");
	}

	@Override
	public boolean updateCredentials(@NonNull Domain domain, @NonNull Credentials current,
			@NonNull Credentials replacement) {
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
		private final ModelObject context;

        private ConjurCredentialStoreAction(ConjurCredentialStore store, ModelObject context) {
            this.store = store;
			this.context = context;
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
        @NonNull
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

		private static final String DISPLAY_NAME = "Conjur Credential Store";
        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }
    }
}