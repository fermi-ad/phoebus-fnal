/*******************************************************************************
 * Copyright (c) 2025 Fermi National Accelerator Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.app.utility.acnet;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import org.phoebus.framework.spi.AppDescriptor;
import org.phoebus.framework.spi.AppInstance;
import org.phoebus.pv.PV;
import org.phoebus.pv.PVPool;
import org.phoebus.ui.javafx.ImageCache;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AppDescriptor for enabling ACNET setting.
 * 
 * @author Zongwei Yuan
 */
public class EnableAcnetSettingApp implements AppDescriptor
{
    private static final Logger logger = Logger.getLogger(EnableAcnetSettingApp.class.getName());

    public static final String DISPLAY_NAME = "Enable Acnet Setting";
    public static final String NAME = "enableSetting";
    public static final Image icon = ImageCache.getImage(EnableAcnetSettingApp.class, "/icons/acnet-setting-16.png");

    private static final String ENABLE_SETTINGS_PV = "acsys://enableSettings";

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
        Platform.runLater(() -> {
            // Show confirmation dialog similar to the BOB file
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Enable ACNET Settings");
            confirmAlert.setHeaderText("Are you sure you want to enable ACNET settings?");
            confirmAlert.setContentText("This will send a command to " + ENABLE_SETTINGS_PV);
            
            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                enableAcnetSettings();
            }
        });
        
        return null;
    }
    
    private void enableAcnetSettings() {
        CompletableFuture.runAsync(() -> {
            PV pv = null;
            try {
                logger.info("Attempting to enable ACNET settings via PV: " + ENABLE_SETTINGS_PV);
                
                // Get PV from pool
                pv = PVPool.getPV(ENABLE_SETTINGS_PV);
                
                // Wait for connection with timeout
                Thread.sleep(1000); // Give PV time to connect
                
                // Write value 1 to enable settings
                pv.asyncWrite(1);
                
                logger.info("Successfully sent enable settings command to " + ENABLE_SETTINGS_PV);
                
                Platform.runLater(() -> {
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("ACNET Settings");
                    successAlert.setHeaderText("Success");
                    successAlert.setContentText("ACNET settings enable command sent successfully.");
                    successAlert.showAndWait();
                });
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to enable ACNET settings", e);
                
                Platform.runLater(() -> {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("ACNET Settings Error");
                    errorAlert.setHeaderText("Failed to enable ACNET settings");
                    errorAlert.setContentText("Error: " + e.getMessage());
                    errorAlert.showAndWait();
                });
            } finally {
                if (pv != null) {
                    PVPool.releasePV(pv);
                }
            }
        });
    }
}
