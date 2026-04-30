/*******************************************************************************
 * Copyright (c) 2025 Fermi National Accelerator Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.app.utility.acnet;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.phoebus.applications.credentialsmanagement.CredentialsManagementStage;
import org.phoebus.framework.spi.AppDescriptor;
import org.phoebus.framework.spi.AppInstance;
import org.phoebus.security.authorization.ServiceAuthenticationProvider;
import org.phoebus.security.store.SecureStore;
import org.phoebus.security.tokens.AuthenticationScope;
import org.phoebus.security.tokens.ScopedAuthenticationToken;
import org.phoebus.ui.javafx.ImageCache;

import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AppDescriptor for enabling ACNET setting.
 * Shows a duration picker dialog; delegates enable/disable to {@link SettingEnableService}.
 *
 * @author Zongwei Yuan
 */
public class EnableAcnetSettingApp implements AppDescriptor
{
    private static final Logger logger = Logger.getLogger(EnableAcnetSettingApp.class.getName());

    public static final String DISPLAY_NAME = "Enable Acnet Setting";
    public static final String NAME = "enableSetting";
    public static final Image icon = ImageCache.getImage(EnableAcnetSettingApp.class, "/icons/acnet-setting-16.png");

    /** Remembers the last chosen duration across button clicks. */
    private static String lastSelection = "Disable";

    @Override
    public String getDisplayName()
    {
        return DISPLAY_NAME;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public AppInstance create()
    {
        Platform.runLater(this::checkCredentialsThenShow);
        return null;
    }

    /**
     * Step 1 — Check Kerberos credential status via SecureStore.
     * <ul>
     *   <li>Token present → go straight to the duration picker.</li>
     *   <li>No token → open the Phoebus Credentials Management dialog (modal)
     *       so the user can log in with Kerberos, then re-check:
     *       success → show picker, still no token → show error message.</li>
     * </ul>
     */
    private void checkCredentialsThenShow()
    {
        if (isKerberosLoggedIn())
        {
            showDurationDialog();
        }
        else
        {
            // Open the standard Phoebus credential manager (modal) — Kerberos tab will be shown
            // because KerberosAuthenticationProvider is registered via ServiceLoader
            try
            {
                List<ServiceAuthenticationProvider> providers =
                        ServiceLoader.load(ServiceAuthenticationProvider.class)
                                     .stream()
                                     .map(ServiceLoader.Provider::get)
                                     .collect(java.util.stream.Collectors.toList());
                SecureStore store = new SecureStore();
                CredentialsManagementStage stage = new CredentialsManagementStage(providers, store);
                // After the modal closes, re-check credentials
                stage.setOnHidden(e -> {
                    if (isKerberosLoggedIn())
                    {
                        showDurationDialog();
                    }
                    else
                    {
                        Alert err = new Alert(Alert.AlertType.WARNING);
                        err.setTitle("ACNET Settings");
                        err.setHeaderText("Kerberos login required");
                        err.setContentText(
                                "No Kerberos credentials found.\n" +
                                "Please log in with your FNAL Kerberos account\n" +
                                "to enable ACNET settings.");
                        err.showAndWait();
                    }
                });
                stage.showAndWait();
            }
            catch (Exception ex)
            {
                logger.log(Level.SEVERE, "Failed to open credentials manager", ex);
                Alert err = new Alert(Alert.AlertType.ERROR);
                err.setTitle("ACNET Settings");
                err.setHeaderText("Cannot open login dialog");
                err.setContentText(ex.getMessage());
                err.showAndWait();
            }
        }
    }

    /** @return true if a valid Kerberos token exists in the SecureStore. */
    private boolean isKerberosLoggedIn()
    {
        try
        {
            SecureStore store = new SecureStore();
            AuthenticationScope scope = AuthenticationScope.fromString("kerberos");
            if (scope == null) scope = AuthenticationScope.LOGBOOK;
            ScopedAuthenticationToken token = store.getScopedAuthenticationToken(scope);
            return token != null && token.getUsername() != null && !token.getUsername().isBlank();
        }
        catch (Exception ex)
        {
            logger.log(Level.WARNING, "Could not read SecureStore for Kerberos check", ex);
            return false;
        }
    }

    private void showDurationDialog()
    {
        final SettingEnableService svc = SettingEnableService.getInstance();

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("ACNET Settings");
        dialog.setHeaderText("Select setting duration:");

        ChoiceBox<String> durationBox = new ChoiceBox<>();
        durationBox.getItems().addAll("Disable", "5 minutes", "20 minutes", "1 hour", "5 hours", "Forever");

        // Show remaining time in header if currently enabled
        if (svc.isEnabled())
        {
            long remaining = svc.remainingSeconds();
            String hint = remaining < 0 ? "Forever" : formatSeconds(remaining);
            dialog.setHeaderText("Currently ENABLED (" + hint + " remaining)\nSelect new duration or Disable:");
        }
        else
        {
            // Service is disabled (possibly just expired) — reset remembered selection
            lastSelection = "Disable";
        }

        // Always restore the last user selection
        durationBox.setValue(lastSelection);

        VBox content = new VBox(8,
                new HBox(10, new Label("Duration:"), durationBox));
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        ButtonType applyBtn  = new ButtonType("Apply");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(applyBtn, cancelBtn);

        dialog.setResultConverter(bt -> bt == applyBtn ? durationBox.getValue() : null);

        dialog.showAndWait().ifPresent(choice -> {
            lastSelection = choice;   // remember for next time
            switch (choice)
            {
                case "Disable":     svc.disable(); break;
                case "5 minutes":   svc.enable(5L); break;
                case "20 minutes":  svc.enable(20L); break;
                case "1 hour":      svc.enable(60L); break;
                case "5 hours":     svc.enable(300L); break;
                case "Forever":     svc.enable(SettingEnableService.DURATION_FOREVER); break;
            }
        });
    }

    /** Format a second count into a human-readable string, e.g. "4h 59m 03s". */
    static String formatSeconds(long totalSeconds)
    {
        if (totalSeconds <= 0) return "0s";
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        if (h > 0)  return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0)  return String.format("%dm %02ds", m, s);
        return String.format("%ds", s);
    }
}
