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
 * Toolbar entry for enabling ACNET setting.
 * 
 * @author FNAL
 */
public class EnableAcnetSettingToolbarEntry implements ToolbarEntry
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
    public Image getIcon()
    {
        return EnableAcnetSettingApp.icon;
    }

    /**
     * DO NOT CHANGE RETURN VALUE!
     * @return The unique id of this {@link ToolbarEntry}.
     */
    @Override
    public String getId()
    {
        return "Enable Acnet Setting";
    }
}
