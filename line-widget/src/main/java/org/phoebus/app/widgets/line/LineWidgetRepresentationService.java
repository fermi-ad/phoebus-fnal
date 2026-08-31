package org.phoebus.app.widgets.line;

import java.util.Map;

import org.csstudio.display.builder.model.WidgetDescriptor;
import org.csstudio.display.builder.representation.WidgetRepresentation;
import org.csstudio.display.builder.representation.WidgetRepresentationFactory;
import org.csstudio.display.builder.representation.spi.WidgetRepresentationsService;

/** SPI that registers the {@link LineRepresentation} for the {@link LineWidget} */
public class LineWidgetRepresentationService implements WidgetRepresentationsService
{
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <TWP, TW> Map<WidgetDescriptor, WidgetRepresentationFactory<TWP, TW>> getWidgetRepresentationFactories()
    {
        return Map.of(LineWidget.WIDGET_DESCRIPTOR,
                       () -> (WidgetRepresentation) new LineRepresentation());
    }
}
