package org.phoebus.app.widgets.line;

import org.csstudio.display.builder.model.DirtyFlag;
import org.csstudio.display.builder.model.UntypedWidgetPropertyListener;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.representation.javafx.JFXUtil;
import org.csstudio.display.builder.representation.javafx.widgets.JFXBaseRepresentation;
import org.csstudio.display.builder.representation.javafx.widgets.PolylineRepresentation.Arrow;
import org.phoebus.app.widgets.line.LineWidget.Direction;

import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;

/** Creates JavaFX item for the {@link LineWidget}
 *  @author FNAL
 */
public class LineRepresentation extends JFXBaseRepresentation<Group, LineWidget>
{
    private final DirtyFlag dirty_geometry = new DirtyFlag();
    private final DirtyFlag dirty_look = new DirtyFlag();
    private final UntypedWidgetPropertyListener geometryChangedListener = this::geometryChanged;
    private final UntypedWidgetPropertyListener lookChangedListener = this::lookChanged;

    @Override
    public Group createJFXNode() throws Exception
    {
        final Line line = new Line();
        line.setStrokeLineCap(StrokeLineCap.BUTT);
        return new Group(line, new Arrow(), new Arrow());
    }

    @Override
    protected void registerListeners()
    {
        super.registerListeners();
        model_widget.propWidth().addUntypedPropertyListener(geometryChangedListener);
        model_widget.propHeight().addUntypedPropertyListener(geometryChangedListener);
        model_widget.propDirection().addUntypedPropertyListener(geometryChangedListener);
        model_widget.propArrows().addUntypedPropertyListener(geometryChangedListener);
        model_widget.propArrowLength().addUntypedPropertyListener(geometryChangedListener);

        model_widget.propLineColor().addUntypedPropertyListener(lookChangedListener);
        model_widget.propLineWidth().addUntypedPropertyListener(lookChangedListener);
        model_widget.propLineStyle().addUntypedPropertyListener(lookChangedListener);
    }

    @Override
    protected void unregisterListeners()
    {
        model_widget.propWidth().removePropertyListener(geometryChangedListener);
        model_widget.propHeight().removePropertyListener(geometryChangedListener);
        model_widget.propDirection().removePropertyListener(geometryChangedListener);
        model_widget.propArrows().removePropertyListener(geometryChangedListener);
        model_widget.propArrowLength().removePropertyListener(geometryChangedListener);

        model_widget.propLineColor().removePropertyListener(lookChangedListener);
        model_widget.propLineWidth().removePropertyListener(lookChangedListener);
        model_widget.propLineStyle().removePropertyListener(lookChangedListener);
        super.unregisterListeners();
    }

    private void geometryChanged(final WidgetProperty<?> property, final Object old_value, final Object new_value)
    {
        dirty_geometry.mark();
        toolkit.scheduleUpdate(this);
    }

    private void lookChanged(final WidgetProperty<?> property, final Object old_value, final Object new_value)
    {
        dirty_look.mark();
        toolkit.scheduleUpdate(this);
    }

    @Override
    public void updateChanges()
    {
        super.updateChanges();

        if (dirty_geometry.checkAndClear())
        {
            final double w = model_widget.propWidth().getValue();
            final double h = model_widget.propHeight().getValue();
            final Direction dir = model_widget.propDirection().getValue();
            final double x1, y1, x2, y2;
            if (dir == Direction.BOTTOM_LEFT_TO_TOP_RIGHT)
            {
                x1 = 0;  y1 = h;
                x2 = w;  y2 = 0;
            }
            else
            {
                x1 = 0;  y1 = 0;
                x2 = w;  y2 = h;
            }

            final int arrows_val;
            switch (model_widget.propArrows().getValue())
            {
            case FROM:
                arrows_val = 1;
                break;
            case TO:
                arrows_val = 2;
                break;
            case BOTH:
                arrows_val = 3;
                break;
            case NONE:
            default:
                arrows_val = 0;
                break;
            }
            final int length = model_widget.propArrowLength().getValue();
            int i = 0;
            for (final Node child : jfx_node.getChildrenUnmodifiable())
            {
                if (child instanceof Line)
                {
                    final Line line = (Line) child;
                    line.setStartX(x1);
                    line.setStartY(y1);
                    line.setEndX(x2);
                    line.setEndY(y2);
                }
                else
                {
                    final Arrow arrow = (Arrow) child;
                    if ((i & arrows_val) != 0)
                    {
                        arrow.setVisible(true);
                        if (i == 1) // Arrow at start, pointing away from the line
                            arrow.adjustPoints(x1, y1, x2, y2, length);
                        else // i == 2: Arrow at end, pointing away from the line
                            arrow.adjustPoints(x2, y2, x1, y1, length);
                    }
                    else
                        arrow.setVisible(false);
                }
                ++i;
            }
        }

        if (dirty_look.checkAndClear())
        {
            final Color color = JFXUtil.convert(model_widget.propLineColor().getValue());
            final int line_width = Math.max(1, model_widget.propLineWidth().getValue());
            for (final Node child : jfx_node.getChildrenUnmodifiable())
            {
                final Shape shape = (Shape) child;
                shape.setStroke(color);
                shape.setStrokeWidth(line_width);
                if (shape instanceof Line)
                {
                    final ObservableList<Double> dashes = shape.getStrokeDashArray();
                    dashes.setAll(JFXUtil.getDashArray(model_widget.propLineStyle().getValue(), line_width));
                }
                else
                    ((Arrow) shape).setFill(color);
            }
        }
    }
}
