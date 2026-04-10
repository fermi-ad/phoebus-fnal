/*******************************************************************************
 * Copyright (c) 2025 Fermi National Accelerator Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 *******************************************************************************/
package org.phoebus.app.utility.acnet;

import org.phoebus.security.authorization.ServiceAuthenticationProvider;
import org.phoebus.security.store.SecureStore;
import org.phoebus.security.tokens.AuthenticationScope;
import org.phoebus.security.tokens.ScopedAuthenticationToken;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ServiceAuthenticationProvider} implementation for Fermilab Kerberos authentication.
 *
 * <p>This provider adds a "Kerberos" row to the Phoebus Credential Manager dialog.
 * Credentials (principal + password) are authenticated against the Fermilab Kerberos KDC
 * via JAAS {@code Krb5LoginModule} and persisted in the Phoebus {@link SecureStore}.
 *
 * @author FNAL 
 */
public class KerberosAuthenticationProvider implements ServiceAuthenticationProvider {

    private static final Logger logger = Logger.getLogger(KerberosAuthenticationProvider.class.getName());

    /** Kerberos realm used at Fermilab */
    private static final String KERBEROS_REALM = "FNAL.GOV";

    @Override
    public AuthenticationScope getAuthenticationScope() {
        return AuthenticationScope.KERBEROS;
    }

    @Override
    public void authenticate(String username, String password) {
        final String principal = username.contains("@") ? username : username + "@" + KERBEROS_REALM;

        try {
            Configuration jaasConfig = buildKerberosConfig(principal);

            CallbackHandler handler = callbacks -> {
                for (Callback cb : callbacks) {
                    if (cb instanceof NameCallback) {
                        ((NameCallback) cb).setName(principal);
                    } else if (cb instanceof PasswordCallback) {
                        ((PasswordCallback) cb).setPassword(password.toCharArray());
                    } else {
                        throw new UnsupportedCallbackException(cb, "Unsupported callback: " + cb.getClass().getName());
                    }
                }
            };

            LoginContext lc = new LoginContext("KerberosLogin", new Subject(), handler, jaasConfig);
            lc.login();

            logger.info("Kerberos authentication succeeded for principal: " + principal);

            try {
                SecureStore store = new SecureStore();
                store.setScopedAuthentication(new ScopedAuthenticationToken(AuthenticationScope.KERBEROS, username, password));
                logger.info("Kerberos credentials saved to SecureStore for user: " + username);
            } catch (Exception storeEx) {
                logger.log(Level.WARNING, "Kerberos login succeeded but could not save to SecureStore", storeEx);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Kerberos authentication failed for principal: " + principal + " — " + e.getMessage());
            throw new RuntimeException("Kerberos authentication failed for " + principal + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void logout(String token) {
        try {
            SecureStore store = new SecureStore();
            store.setScopedAuthentication(new ScopedAuthenticationToken(AuthenticationScope.KERBEROS, null, null));
            logger.info("Kerberos credentials cleared from SecureStore");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to clear Kerberos credentials from SecureStore", e);
        }
    }

    /**
     * Builds a programmatic JAAS {@link Configuration} for {@code Krb5LoginModule}
     * so that no external {@code jaas.conf} file is required.
     *
     * @param principal The Kerberos principal (user@REALM)
     * @return A {@link Configuration} suitable for {@link LoginContext}
     */
    private static Configuration buildKerberosConfig(String principal) {
        Map<String, String> options = new HashMap<>();
        options.put("useTicketCache", "false");
        options.put("doNotPrompt", "false");   // false -> use our CallbackHandler for username/password
        options.put("isInitiator", "true");
        options.put("storeKey", "true");
        options.put("principal", principal);
        options.put("debug", "false");

        AppConfigurationEntry entry = new AppConfigurationEntry(
                "com.sun.security.auth.module.Krb5LoginModule",
                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                options
        );

        return new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return new AppConfigurationEntry[]{entry};
            }
        };
    }
}
