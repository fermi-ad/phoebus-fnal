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
import org.phoebus.pv.PV;
import org.phoebus.pv.PVPool;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton service that manages the ACNET setting enable/disable state.
 * <p>
 * Writes {@code 1} to the enable PV when settings are enabled and
 * schedules an automatic write of {@code 0} after the chosen duration.
 *
 * @author Zongwei Yuan
 */
public class SettingEnableService
{
    private static final Logger logger = Logger.getLogger(SettingEnableService.class.getName());
    private static final String ENABLE_SETTINGS_PV = "acsys://enableSettings";

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

    /** Separate executor for PV writes so blocking I/O never stalls the timer. */
    private final java.util.concurrent.ExecutorService pvWriter =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SettingEnablePVWriter");
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
     */
    public void enable(long durationMinutes)
    {
        // Cancel any running expiry timer
        cancelExpiryTask();

        // Enable widgets immediately — don't wait for PV write to succeed
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

        // Write to PV in background (best-effort — failures are logged but don't block UI)
        writePV(1, () -> logger.log(Level.FINE, "Enable PV write acknowledged."));
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

        // Write to PV in background (best-effort)
        writePV(0, () -> logger.log(Level.FINE, "Disable PV write acknowledged."));

        // Property listener updates JavaFX nodes — must run on FX thread.
        // disableInternal() may be called from the scheduler thread (timer expiry),
        // so always marshal onto the FX thread here.
        Platform.runLater(() -> {
            WritablePVWidget.setGlobalWriteEnabled(false);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("ACNET Settings");
            alert.setHeaderText("Settings disabled");
            alert.setContentText("The ACNET setting enable period has expired.\n"
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

    /**
     * Writes {@code value} to the enable PV asynchronously, then runs
     * {@code onSuccess} on the scheduler thread.
     */
    private void writePV(int value, Runnable onSuccess)
    {
        pvWriter.execute(() -> {
            PV pv = null;
            try
            {
                pv = PVPool.getPV(ENABLE_SETTINGS_PV);
                Thread.sleep(500); // brief wait for connection
                pv.asyncWrite(value);
                onSuccess.run();
            }
            catch (Exception e)
            {
                logger.log(Level.SEVERE, "Failed to write " + value
                        + " to " + ENABLE_SETTINGS_PV, e);
                final String msg = e.getMessage();
                Platform.runLater(() -> {
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("ACNET Settings Error");
                    err.setHeaderText("Failed to write enable PV");
                    err.setContentText(msg);
                    err.show();
                });
            }
            finally
            {
                if (pv != null) PVPool.releasePV(pv);
            }
        });
    }
}
