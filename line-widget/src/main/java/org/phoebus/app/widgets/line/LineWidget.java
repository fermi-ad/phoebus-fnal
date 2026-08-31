/*******************************************************************************
 * FNAL Line widget.
 *
 * A simple two-endpoint straight-line connector widget, meant as an easier
 * alternative to Polyline for the common case of "draw a line connecting
 * two other widgets". Unlike Polyline (whose 'points' property starts out
 * empty and must be edited point-by-point after the widget is created),
 * Line reuses the standard x/y/width/height bounding box that every simple
 * widget (Rectangle, Ellipse, ...) already gets for free from the editor's
 * rubber-band widget creation and resize handling. The line is simply drawn
 * corner-to-corner across that bounding box, so dragging out the widget
 * immediately draws a visible line - no separate point-editing step needed.
 *******************************************************************************/
package org.phoebus.app.widgets.line;

import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.newIntegerPropertyDescriptor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propLineColor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propLineStyle;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propLineWidth;

import java.util.List;

import org.csstudio.display.builder.model.Widget;
import org.csstudio.display.builder.model.WidgetCategory;
import org.csstudio.display.builder.model.WidgetDescriptor;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.model.WidgetPropertyCategory;
import org.csstudio.display.builder.model.WidgetPropertyDescriptor;
import org.csstudio.display.builder.model.properties.EnumWidgetProperty;
import org.csstudio.display.builder.model.properties.LineStyle;
import org.csstudio.display.builder.model.widgets.MacroWidget;
import org.phoebus.ui.color.WidgetColor;

/** Widget that displays a simple straight line across its bounding box
 *  @author FNAL
 */
@SuppressWarnings("nls")
public class LineWidget extends MacroWidget
{
    /** Widget descriptor */
    public static final WidgetDescriptor WIDGET_DESCRIPTOR =
        new WidgetDescriptor("fnal_line", WidgetCategory.GRAPHIC,
            "Line",
            "/icons/polyline.png",
            "A simple straight line, drawn corner-to-corner across the widget's bounding box. " +
            "Easier to draw than Polyline for a simple line connecting two other widgets: " +
            "just drag out the widget and resize/move it like a Rectangle.")
    {
        @Override
        public Widget createWidget()
        {
            return new LineWidget();
        }
    };

    /** Direction of the line across the bounding box */
    public enum Direction
    {
        /** Top-left corner to bottom-right corner ( \ ) */
        TOP_LEFT_TO_BOTTOM_RIGHT("Top-Left to Bottom-Right"),
        /** Bottom-left corner to top-right corner ( / ) */
        BOTTOM_LEFT_TO_TOP_RIGHT("Bottom-Left to Top-Right");

        private final String name;

        private Direction(final String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    /** Line 'arrows', mirroring Polyline's arrow options */
    public enum Arrows
    {
        /** No arrows */
        NONE("None"),
        /** Arrow at the line's start point */
        FROM("Start Arrow"),
        /** Arrow at the line's end point */
        TO("End Arrow"),
        /** Arrows at both ends */
        BOTH("Both Arrows");

        private final String name;

        private Arrows(final String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    /** 'direction' */
    private static final WidgetPropertyDescriptor<Direction> propDirection =
        new WidgetPropertyDescriptor<>(WidgetPropertyCategory.DISPLAY, "direction", "Direction")
    {
        @Override
        public EnumWidgetProperty<Direction> createProperty(final Widget widget, final Direction default_value)
        {
            return new EnumWidgetProperty<>(this, widget, default_value);
        }
    };

    /** 'arrows' */
    private static final WidgetPropertyDescriptor<Arrows> propArrows =
        new WidgetPropertyDescriptor<>(WidgetPropertyCategory.DISPLAY, "arrows", "Arrows")
    {
        @Override
        public EnumWidgetProperty<Arrows> createProperty(final Widget widget, final Arrows default_value)
        {
            return new EnumWidgetProperty<>(this, widget, default_value);
        }
    };

    /** 'arrow_length' */
    private static final WidgetPropertyDescriptor<Integer> propArrowLength =
        newIntegerPropertyDescriptor(WidgetPropertyCategory.DISPLAY, "arrow_length", "Arrow Length", 2, Integer.MAX_VALUE);

    private volatile WidgetProperty<WidgetColor> line_color;
    private volatile WidgetProperty<Integer> line_width;
    private volatile WidgetProperty<LineStyle> line_style;
    private volatile WidgetProperty<Direction> direction;
    private volatile WidgetProperty<Arrows> arrows;
    private volatile WidgetProperty<Integer> arrow_length;

    /** Constructor */
    public LineWidget()
    {
        super(WIDGET_DESCRIPTOR.getType());
    }

    @Override
    protected void defineProperties(final List<WidgetProperty<?>> properties)
    {
        super.defineProperties(properties);
        properties.add(line_width = propLineWidth.createProperty(this, 3));
        properties.add(line_color = propLineColor.createProperty(this, new WidgetColor(0, 0, 255)));
        properties.add(line_style = propLineStyle.createProperty(this, LineStyle.SOLID));
        properties.add(direction = propDirection.createProperty(this, Direction.TOP_LEFT_TO_BOTTOM_RIGHT));
        properties.add(arrows = propArrows.createProperty(this, Arrows.NONE));
        properties.add(arrow_length = propArrowLength.createProperty(this, 20));
    }

    /** @return 'line_color' property */
    public WidgetProperty<WidgetColor> propLineColor()
    {
        return line_color;
    }

    /** @return 'line_width' property */
    public WidgetProperty<Integer> propLineWidth()
    {
        return line_width;
    }

    /** @return 'line_style' property */
    public WidgetProperty<LineStyle> propLineStyle()
    {
        return line_style;
    }

    /** @return 'direction' property */
    public WidgetProperty<Direction> propDirection()
    {
        return direction;
    }

    /** @return 'arrows' property */
    public WidgetProperty<Arrows> propArrows()
    {
        return arrows;
    }

    /** @return 'arrow_length' property */
    public WidgetProperty<Integer> propArrowLength()
    {
        return arrow_length;
    }
}
