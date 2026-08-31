package org.phoebus.app.widgets.line;

import java.util.Collection;
import java.util.List;

import org.csstudio.display.builder.model.WidgetDescriptor;
import org.csstudio.display.builder.model.spi.WidgetsService;

/** SPI that registers the FNAL {@link LineWidget} with the {@code WidgetFactory} */
public class LineWidgetService implements WidgetsService
{
    @Override
    public Collection<WidgetDescriptor> getWidgetDescriptors()
    {
        return List.of(LineWidget.WIDGET_DESCRIPTOR);
    }
}
