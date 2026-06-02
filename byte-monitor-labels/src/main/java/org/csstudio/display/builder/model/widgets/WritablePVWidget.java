/*******************************************************************************
 * Copyright (c) 2015-2016 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.model.widgets;

import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.runtimePropPVWritable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.csstudio.display.builder.model.WidgetProperty;

/** Base class for all widgets that write to a primary PV.
 *
 *  <p>Default WidgetRuntime will connect PV to "pv_name",
 *  update "pv_value" with received updates,
 *  and update "pv_writable" to reflect write access.
 *
 *  <p>A global write-enable flag ({@link #setGlobalWriteEnabled(boolean)}) allows
 *  an operator-level enable/disable of all writable widgets at once.
 *
 *  @author Kay Kasemir
 *  @author Zongwei Yuan (global enable/disable)
 */
public class WritablePVWidget extends PVWidget
{
    // -----------------------------------------------------------------------
    // Global write-enable registry
    // -----------------------------------------------------------------------

    /** Global flag: true = operator has enabled settings, false = locked. */
    private static volatile boolean globalWriteEnabled = false;

    /** Weak references to all live WritablePVWidget instances. */
    private static final CopyOnWriteArrayList<WeakReference<WritablePVWidget>> ALL_INSTANCES =
            new CopyOnWriteArrayList<>();

    /**
     * Enable or disable writing on ALL live writable widgets.
     * <p>Called by {@code SettingEnableService} when the operator
     * enables or the timer expires.
     *
     * @param enabled {@code true} to allow writes, {@code false} to lock
     */
    public static void setGlobalWriteEnabled(final boolean enabled)
    {
        globalWriteEnabled = enabled;
        // Propagate to every live widget; prune dead refs along the way
        ALL_INSTANCES.removeIf(ref -> {
            final WritablePVWidget widget = ref.get();
            if (widget == null)
                return true;  // collected — remove from list
            final WidgetProperty<Boolean> prop = widget.pv_writable;
            if (prop != null)
            {
                // Raise applyingGlobal so the listener does NOT overwrite pvActuallyWritable
                widget.applyingGlobal = true;
                try { prop.setValue(enabled && widget.pvActuallyWritable); }
                finally { widget.applyingGlobal = false; }
            }
            return false;
        });
    }

    /** @return Current global write-enable state. */
    public static boolean isGlobalWriteEnabled()
    {
        return globalWriteEnabled;
    }

    // -----------------------------------------------------------------------

    /** Whether the connected PV itself is writable (independent of global flag). */
    private volatile boolean pvActuallyWritable = false;
    /** Guard against recursive listener calls when we correct pv_writable. */
    private volatile boolean applyingGate = false;
    /** Guard: setGlobalWriteEnabled is driving pv_writable — don't overwrite pvActuallyWritable. */
    private volatile boolean applyingGlobal = false;

    private volatile WidgetProperty<Boolean> pv_writable;

    /** @param type Widget type. */
    public WritablePVWidget(final String type)
    {
        super(type);
        ALL_INSTANCES.add(new WeakReference<>(this));
    }

    /** @param type Widget type.
     *  @param default_width Default widget width.
     *  @param default_height Default widget height.
     */
    public WritablePVWidget(final String type, final int default_width, final int default_height)
    {
        super(type, default_width, default_height);
        ALL_INSTANCES.add(new WeakReference<>(this));
    }

    @Override
    protected void defineProperties (final List<WidgetProperty<?>> properties )
    {
        super.defineProperties(properties);
        // Always start locked; the listener below will allow true only when global is enabled.
        pv_writable = runtimePropPVWritable.createProperty(this, false);
        properties.add(pv_writable);

        // Intercept every runtime update of pv_writable so we can gate it.
        pv_writable.addPropertyListener((prop, old, runtimeWants) -> {
            if (applyingGate)   return;   // our own correction — skip
            if (applyingGlobal) return;   // setGlobalWriteEnabled driving — don't touch pvActuallyWritable
            final boolean want = Boolean.TRUE.equals(runtimeWants);
            pvActuallyWritable = want;         // remember PV's true writability
            final boolean effective = want && globalWriteEnabled;
            if (effective != want)
            {
                applyingGate = true;
                try { pv_writable.setValue(effective); }
                finally { applyingGate = false; }
            }
        });
    }

    /** @return 'pv_writable' property */
    public final WidgetProperty<Boolean> runtimePropPVWritable()
    {
        return pv_writable;
    }
}
