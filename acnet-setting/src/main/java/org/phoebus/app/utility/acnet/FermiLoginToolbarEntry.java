/*******************************************************************************
 * Copyright (c) 2025 Fermi National Accelerator Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.app.utility.acnet;

import javafx.scene.image.Image;
import org.phoebus.framework.workbench.ApplicationService;
import org.phoebus.ui.spi.ToolbarEntry;

/**
 * Toolbar entry for Fermilab Keycloak login.
 * Adds a "Login" button to the Phoebus toolbar.
 *
 * @author FNAL
 */
public class FermiLoginToolbarEntry implements ToolbarEntry
{
    @Override
    public Void call() throws Exception
    {
        ApplicationService.createInstance(FermiLoginApp.NAME);
        return null;
    }

    @Override
    public String getName()
    {
        return FermiLoginApp.DISPLAY_NAME;
    }

    @Override
    public Image getIcon()
    {
        return FermiLoginApp.icon;
    }

    /**
     * DO NOT CHANGE RETURN VALUE!
     * @return The unique id of this {@link ToolbarEntry}.
     */
    @Override
    public String getId()
    {
        return "Fermi Login";
    }
}
