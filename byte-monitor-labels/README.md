# byte-monitor-labels

FNAL patch module for the Phoebus `ByteMonitor` widget.

## What it does

Adds **per-LED ON/OFF text labels** displayed centered inside each LED circle of the ByteMonitor widget.

Each LED independently shows its own label based on its bit state:
- Bit = 1 → shows `on_label` text (e.g. `ON`)
- Bit = 0 → shows `off_label` text (e.g. `OFF`)

## New widget properties

| Property | XML tag | Default | Description |
|---|---|---|---|
| Off Label | `off_label` | `""` | Text shown inside each LED when its bit is 0 |
| On Label  | `on_label`  | `""` | Text shown inside each LED when its bit is 1 |

If both are empty, the existing static `labels` array behavior is preserved.

## Example `.bob` snippet

```xml
<widget type="byte_monitor" version="2.0.0">
  <pv_name>MY:PV:NAME</pv_name>
  <width>320</width>
  <height>80</height>
  <font><font family="Liberation Sans" style="BOLD" size="11.0"/></font>
  <foreground_color><color red="255" green="255" blue="255"/></foreground_color>
  <off_label>OFF</off_label>
  <on_label>ON</on_label>
</widget>
```

## How it works (classpath shadowing)

This module contains patched versions of the following classes **in the same packages** as the upstream `app-display-model` and `app-display-representation-javafx` JARs:

| Patched file | Original upstream location |
|---|---|
| `org.csstudio.display.builder.model.widgets.ByteMonitorWidget` | `app-display-model` |
| `org.csstudio.display.builder.representation.javafx.widgets.ByteMonitorRepresentation` | `app-display-representation-javafx` |
| `org.csstudio.display.builder.model.Messages` | `app-display-model` |
| `org.csstudio.display.builder.model.messages.properties` | `app-display-model` |

In `product-fnal/pom.xml` this JAR is declared **before** the upstream `product` dependency, so the JVM classloader finds our patched classes first.

## Changes vs upstream

### `ByteMonitorWidget.java`
- Added `propOffLabel` and `propOnLabel` volatile properties (type `String`, default `""`)
- Added `propOffLabel()` and `propOnLabel()` accessor methods
- Registered both in `defineProperties()`

### `ByteMonitorRepresentation.java`
- Added `volatile int[] value_indices` field to store per-bit on/off state
- `contentChanged()`: stores `value_indices = indices` alongside `value_colors`
- `addLEDs()`:
  - Fixed LED centering: `circle_r = Math.min(led_w, led_h)/2`, center at `(x+led_w/2, y+led_h/2)`
  - Creates a `Label` per LED, centered (`Pos.CENTER`), with `label.resize()` called **after** `pane.getChildren().add(label)`
- `updateChanges()`: sets label text per-LED using `value_indices[i] != 0 ? on_label : off_label`

### `Messages.java` + `messages.properties`
- Added `WidgetProperties_ShowWarnings` field
- Required by `LinearMeterWidget` (v5.0.2) static initializer — prevents `NoSuchFieldError` crash of `WidgetFactory`
