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
import org.phoebus.ui.spi.MenuEntry;

/**
 * Application menu entry for Fermilab Keycloak login.
 * Appears under the "Utility" menu.
 *
 * @author FNAL
 */
public class FermiLoginMenuEntry implements MenuEntry
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
    public String getMenuPath()
    {
        return "Utility";
    }

    @Override
    public Image getIcon()
    {
        return FermiLoginApp.icon;
    }
}
