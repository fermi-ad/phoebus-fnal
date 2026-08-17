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
import org.csstudio.display.builder.model.widgets.WritablePVWidget;
import org.phoebus.pv.acsys.ACsys_PVConn;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton service that manages the setting enable/disable state.
 * <p>
 * Writes {@code 1} to the enable PV when settings are enabled and
 * schedules an automatic write of {@code 0} after the chosen duration.
 *
 * @author Zongwei Yuan
 */
public class SettingEnableService
{
    private static final Logger logger = Logger.getLogger(SettingEnableService.class.getName());

    /** Sentinel value meaning "never expire". */
    public static final long DURATION_FOREVER = -1L;

    // --- singleton -----------------------------------------------------------
    private static final SettingEnableService INSTANCE = new SettingEnableService();
    public static SettingEnableService getInstance() { return INSTANCE; }

    // --- state ---------------------------------------------------------------
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SettingEnableTimer");
                t.setDaemon(true);
                return t;
            });

    /** Background executor for DPM calls so blocking I/O never stalls the UI. */
    private final java.util.concurrent.ExecutorService dpmCaller =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SettingEnableDPMCaller");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> expiryTask;
    private volatile boolean enabled = false;
    /** Wall-clock time (ms) when settings will expire, or -1 for forever. */
    private volatile long expiresAt = -1L;

    private SettingEnableService() { /* singleton */ }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** @return {@code true} if settings are currently enabled. */
    public boolean isEnabled() { return enabled; }

    /**
     * Enable settings for the given duration.
     *
     * @param durationMinutes minutes until auto-disable, or {@link #DURATION_FOREVER}
     * @param role            ACSys role to pass to DPM (e.g. Kerberos username)
     */
    public void enable(long durationMinutes, String role)
    {
        // Cancel any running expiry timer
        cancelExpiryTask();

        // Enable widgets immediately — don't wait for DPM call to succeed
        enabled = true;
        WritablePVWidget.setGlobalWriteEnabled(true);

        if (durationMinutes == DURATION_FOREVER)
        {
            expiresAt = -1L;
        }
        else
        {
            expiresAt = System.currentTimeMillis() + durationMinutes * 60_000L;
            expiryTask = scheduler.schedule(
                    this::disableInternal,
                    durationMinutes,
                    TimeUnit.MINUTES);
        }

        // Call DPM directly on a background thread — no Swing dialogs needed
        dpmCaller.execute(() -> {
            try
            {
                ACsys_PVConn.enableSettings(role);
                logger.log(Level.INFO, "ACSys settings enabled with role: " + role);
            }
            catch (Exception e)
            {
                logger.log(Level.SEVERE, "Failed to enable ACSys settings with role: " + role, e);
                final String msg = e.getMessage();
                Platform.runLater(() -> {
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Enable Settings Error");
                    err.setHeaderText("Failed to enable ACSys settings");
                    err.setContentText(msg);
                    err.show();
                });
            }
        });
    }

    /** Immediately disable settings (e.g. called by timer or manual action). */
    public void disable()
    {
        cancelExpiryTask();
        disableInternal();
    }

    /**
     * @return remaining seconds until expiry, 0 if already expired,
     *         or {@link #DURATION_FOREVER} if enabled forever.
     */
    public long remainingSeconds()
    {
        if (!enabled) return 0L;
        if (expiresAt == -1L) return DURATION_FOREVER;
        long rem = (expiresAt - System.currentTimeMillis()) / 1000L;
        return Math.max(rem, 0L);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void disableInternal()
    {
        // Update state flags immediately (safe from any thread)
        enabled = false;
        expiresAt = -1L;

        // Property listener updates JavaFX nodes — must run on FX thread.
        // disableInternal() may be called from the scheduler thread (timer expiry),
        // so always marshal onto the FX thread here.
        Platform.runLater(() -> {
            WritablePVWidget.setGlobalWriteEnabled(false);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Enable Settings");
            alert.setHeaderText("Settings disabled");
            alert.setContentText("The setting enable period has expired.\n"
                    + "All writable widgets are now locked.");
            alert.show();
        });
    }

    private void cancelExpiryTask()
    {
        if (expiryTask != null && !expiryTask.isDone())
        {
            expiryTask.cancel(false);
            expiryTask = null;
        }
    }
}
