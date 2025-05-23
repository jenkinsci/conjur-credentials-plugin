package org.conjur.jenkins.credentials;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.ModelObject;
import org.conjur.jenkins.api.ConjurAPI;
import org.conjur.jenkins.conjursecrets.ConjurSecretCredentials;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * Retrieves the Credentail Supplier for context
 *
 */
public class ConjurCredentialsSupplier implements Supplier<Collection<StandardCredentials>> {

    private static final Logger LOGGER = Logger.getLogger(ConjurCredentialsSupplier.class.getName());
    private ModelObject context;

    /**
     * Private constructor
     * @param context to which Supplier belongs
     */
    private ConjurCredentialsSupplier(ModelObject context) {
        super();
        this.context = context;
    }

    /**
     * Non standard constructor
     *
     * @param context ModelObject context
     * @return StandardCredentials collection
     */
    public static Supplier<Collection<StandardCredentials>> standard(ModelObject context) {
        return new ConjurCredentialsSupplier(context);
    }

    /**
     *
     * @return Context
     */
    private ModelObject getContext() {
        return this.context;
    }

	/**
	 * Method to retrieve the resources from Conjur and convert them to StandardCredentials
	 * 
	 * @return collection of StandardCredential
	 */
	@SuppressFBWarnings
    @Override
    public Collection<StandardCredentials> get()
    {
        // Log context information
        if (getContext() == null)
        {
            return Collections.emptyList();
        }

        Collection<StandardCredentials> allCredentials = new ArrayList<>();

        try {
            allCredentials = ConjurAPI.getCredentialsForContext(StandardCredentials.class, getContext() );
            if( allCredentials != null ) {
                for (Credentials cred : allCredentials) {
                    if (cred instanceof ConjurSecretCredentials) {
                        ((ConjurSecretCredentials) cred).setStoredInConjurStorage(true);
                        LOGGER.log(Level.FINEST, "ConjurCredentialStore: found credentials will set it comes from store!");
                    }
                }
            }
        }catch( Exception e )
        {
            LOGGER.log(Level.SEVERE, String.format("EXCEPTION: CredentialSuplier returned %s", e.getMessage() ) );
        }
        return allCredentials;
    }
}