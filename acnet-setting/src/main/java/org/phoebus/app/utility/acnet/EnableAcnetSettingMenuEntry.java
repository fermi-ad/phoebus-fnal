/*******************************************************************************
 * Copyright (c) 2025 Fermi National Accelerator Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.app.utility.acnet;

import org.phoebus.framework.workbench.ApplicationService;
import org.phoebus.ui.spi.MenuEntry;
import javafx.scene.image.Image;

/**
 * MenuEntry for enabling ACNET setting.
 * 
 * @author FNAL
 */
public class EnableAcnetSettingMenuEntry implements MenuEntry
{
    @Override
    public Void call() throws Exception
    {
        ApplicationService.createInstance(EnableAcnetSettingApp.NAME);
        return null;
    }

    @Override
    public String getName()
    {
        return EnableAcnetSettingApp.DISPLAY_NAME;
    }

    @Override
    public String getMenuPath()
    {
        return "Utility";
    }

    @Override
    public Image getIcon()
    {
        return EnableAcnetSettingApp.icon;
    }
}
