package org.conjur.jenkins.credentials;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ModelObject;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.conjur.jenkins.api.ConjurAPI;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Provides the ConjurCredentails extends CredentialProvider
 */
@Extension(optional = true, ordinal = 1)
public class ConjurCredentialProvider extends CredentialsProvider {

	private static final Logger LOGGER = Logger.getLogger(ConjurCredentialProvider.class.getName());
	private static final ConcurrentHashMap<String, Supplier<Collection<StandardCredentials>>> allCredentialSuppliers = new ConcurrentHashMap<String, Supplier<Collection<StandardCredentials>>>();


	/**
	 * Returns the Credentials as List based on the type,itemGroup and
	 * authentication
	 *
	 * @param type               return the Item/job type
	 * @param itemGroup          return the itemGroup if the job type is multifolder
	 * @param authentication     authentication details
	 * @param domainRequirements provides domain requirements.
	 */
	@Override
	public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type, @NonNull ItemGroup itemGroup,
														  @NonNull Authentication authentication, @NonNull List<DomainRequirement> domainRequirements) {

		return getCredentialsFromSupplier(type, itemGroup, authentication);
	}

	/**
	 * returns the credentials from the supplier for the item,type and
	 * authentication
	 */
	@Override
	@NonNull
	public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type, @NonNull Item item,
			@NonNull Authentication authentication, @NonNull List<DomainRequirement> domainRequirements) {
		return getCredentialsFromSupplier(type, item, authentication);

	}

	/**
	 * returns the Credentials as List based on the type,itemGroup and
	 * authentication
	 */
	@Override
	@NonNull
	public <C extends Credentials> List<C> getCredentials(@NonNull Class<C> type, ItemGroup itemGroup,
			Authentication authentication) {
		return getCredentialsFromSupplier(type, itemGroup, authentication);
	}

	/**
	 * Get credentials for context
	 * @param type class type which will be searched for
	 * @param context the context for which credentials will be returned
	 * @return Array of Credentials
	 * @param <C> class type
	 */
	<C extends Credentials> List<C> getCredentials( @NonNull Class<C> type, ModelObject context )
	{
		List<C> creds = new ArrayList<>();
		try {
			getStore(context);

			Supplier<Collection<StandardCredentials>> currentCredentialSupplier = allCredentialSuppliers.get(String.valueOf(context.hashCode()));

			if (currentCredentialSupplier != null) {
				Collection<StandardCredentials> addNewCredentials = currentCredentialSupplier.get();
				if( addNewCredentials != null ) {
					creds.addAll(addNewCredentials.stream().filter(c -> type.isAssignableFrom(c.getClass())).map(type::cast)
							.collect(Collectors.toList()));
				}
			}
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, String.format("Getting credentials failed. Exception: %s", e.toString() ) );
		}

		return creds;
	}

	/**
	 * Get credentials from provider
	 *
	 * @param type type of credential class which will be returned
	 * @param context current context
	 * @param authentication
	 * @return
	 * @param <C> Credentials in list
	 */
	private <C extends Credentials> List<C> getCredentialsFromSupplier(@NonNull Class<C> type, ModelObject context,
			Authentication authentication) {
		List<C> creds = new ArrayList<C>();

		LOGGER.log(Level.FINEST, String.format("getCredentialsFromSupplier type: %s context: %s", type.toString(), context.getDisplayName() ) );

		ItemGroup<?> locg = null;

		// check authentication

		if (ACL.SYSTEM.equals(authentication))
		{
			// get credentials for current item
			if( context instanceof AbstractFolder)
			{
				locg = (ItemGroup<?>) context;
			}
			else if( context instanceof Item )
			{
				locg = (ItemGroup<?>)(((Item) context).getParent());

				creds.addAll( getCredentials(type, context) );

				if( !ConjurAPI.isInheritanceOn( context ) )
				{
					return creds;
				}
			}

			// get credentials from up folders
			// only if its not last entry , so Jenkins

			if( !(context instanceof hudson.model.Hudson) ) {
				for (ItemGroup<?> g = locg; g instanceof AbstractFolder; g = (AbstractFolder.class.cast(g)).getParent())
				{
					// get credentials from Conjur assigned to context
					creds.addAll( getCredentials(type, g) );

					// we dont want to get credentials from upper levels
					if( !ConjurAPI.isInheritanceOn( g ) )
					{
						LOGGER.log(Level.FINEST, String.format("Inheritance stopped on %s", g.getFullName() ) );
						break;
					}
				}
			}

			try {
				// get credentials from Conjur assigned to context
				creds.addAll( getCredentials(type, Jenkins.get()) );
			}
			catch (IllegalStateException e )
			{
				LOGGER.log(Level.FINEST, String.format("Getting global credentials exception: %s", e.toString() ) );
			}
		}
		LOGGER.log(Level.FINEST, String.format("Return credentials: %d", creds.size() ) );

		return creds;
	}

	/**
	 * Method to return the Conjur Credential Store
	 *
	 * @param object to which Store will be assigned
	 * @return the ConjurCredentailStore based on the ModelObject
	 */
	@Override
	public ConjurCredentialStore getStore(ModelObject object) {
		ConjurCredentialStore store = null;
		Supplier<Collection<StandardCredentials>> supplier = null;

		if (object != null)
		{
			String key = String.valueOf(object.hashCode());

			try {
				if (ConjurCredentialStore.isStoreContainsKey(key))
				{
					LOGGER.log(Level.FINEST, String.format("GetStore EXISTING ConjurCredentialProvider: %s object %s hash %s"
							, object.getClass().getName() , object.toString() , object.hashCode( ) ) );
					store = ConjurCredentialStore.getCredentialStore(key);
				}
				else
				{
					LOGGER.log(Level.FINEST, String.format("GetStore CREATE, key: %s object %s", key, object.toString() ) );
					store = new ConjurCredentialStore(this, object);

					supplier = memoizeWithExpiration(ConjurCredentialsSupplier.standard(object), Duration.ofSeconds(120));
					ConjurCredentialStore.putCredentialStore(key, store);

					allCredentialSuppliers.put(key, supplier);
				}
			} catch (Exception ex) {
				LOGGER.log(Level.SEVERE, String.format("There is a problem with Storage: %s", ex.getMessage() ) );
			}
		}

		return store;
	}

	/**
	 *
	 * @return Map containing all credential suppliers
	 */
	public static ConcurrentMap<String, Supplier<Collection<StandardCredentials>>> getAllCredentialSuppliers()
	{
		return allCredentialSuppliers;
	}

	/**
	 * @return iconClassName
	 */
	@Override
	public String getIconClassName() {
		return "icon-conjur-credentials-store";
	}

	/**
	 * check for the expiration for Supplier based on duration to refresh
	 *
	 * @param <T> class type
	 * @param base object of class type
	 * @param duration expiration time
	 * @return supplier
	 */
	public static <T> Supplier<T> memoizeWithExpiration(Supplier<T> base, Duration duration) {
		return ConjurCustomSuppliers.memoizeWithExpiration(base, duration);
	}

	/**
	 * Put credentials into store pointed by key
	 *
	 * @param ccp ConjurCredentialProvider
	 * @param object which will be stored
	 * @param key
	 * @return ConjurCredentialStore
	 */
	public static ConjurCredentialStore putCredentials( ConjurCredentialProvider ccp, ModelObject object, String key )
	{
		ConjurCredentialStore store = new ConjurCredentialStore(ccp, object);
		Supplier<Collection<StandardCredentials>> supplier = memoizeWithExpiration(ConjurCredentialsSupplier.standard(object), Duration.ofSeconds(120));
		ConjurCredentialStore.putCredentialStore(key, store);
		allCredentialSuppliers.put(key, supplier);
		return store;
	}
}
