package myau.clickgui.bridge;

import myau.clickgui.value.Value;
import myau.clickgui.value.settings.BooleanValue;
import myau.clickgui.value.settings.ColorValue;
import myau.clickgui.value.settings.ModeValue;
import myau.clickgui.value.settings.NumberValue;
import myau.clickgui.value.settings.StringValue;
import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.TextProperty;

public final class BoundValues {
    private BoundValues() {
    }
    public interface Bound {
        Property<?> getProperty();
    }

    public static class BoolValue extends BooleanValue implements Bound {
        private final BooleanProperty property;

        public BoolValue(Object parent, BooleanProperty property) {
            super(parent, property.getName(), property.getName(), property.getValue());
            this.property = property;
        }

        @Override
        public boolean isEnabled() {
            return this.property.getValue();
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.property.setValue(enabled);
        }

        @Override
        public Property<?> getProperty() {
            return this.property;
        }
    }

    public static class NumValue extends NumberValue implements Bound {
        private final Property<?> property;
        private final boolean floating;

        private NumValue(Object parent, Property<?> property, double value, double min, double max,
                         double increment, boolean floating) {
            super(parent, property.getName(), value, min, max, increment);
            this.property = property;
            this.floating = floating;
        }

        public static NumValue of(Object parent, IntProperty property) {
            return new NumValue(parent, property, property.getValue(),
                    property.getMinimum(), property.getMaximum(), 1.0D, false);
        }

        public static NumValue of(Object parent, PercentProperty property) {
            return new NumValue(parent, property, property.getValue(),
                    property.getMinimum(), property.getMaximum(), 1.0D, false);
        }

        public static NumValue of(Object parent, FloatProperty property) {
            return new NumValue(parent, property, property.getValue(),
                    property.getMinimum(), property.getMaximum(), 0.01D, true);
        }

        @Override
        public double getValue() {
            Object current = this.property.getValue();
            return current instanceof Number ? ((Number) current).doubleValue() : 0.0D;
        }

        @Override
        public void setValue(double value) {
            double clamped = Math.max(this.getMin(), Math.min(this.getMax(), value));
            if (this.floating) {
                this.property.setValue(Math.round(clamped * 100.0D) / 100.0F);
            } else {
                this.property.setValue((int) Math.round(clamped));
            }
        }

        @Override
        public Property<?> getProperty() {
            return this.property;
        }
    }

    public static class SelectValue extends ModeValue implements Bound {
        private final ModeProperty property;
        private final String[] modes;

        public SelectValue(Object parent, ModeProperty property, String[] modes, Value[] modeValues) {
            super(parent, property.getName(), modeValues);
            this.property = property;
            this.modes = modes;
        }

        private int index() {
            Integer current = this.property.getValue();
            return current == null ? 0 : current;
        }

        @Override
        public Value getActiveMode() {
            int idx = this.index();
            java.util.List<Value> list = this.getModes();
            return idx >= 0 && idx < list.size() ? list.get(idx) : list.get(0);
        }

        @Override
        public String getMode() {
            int idx = this.index();
            return idx >= 0 && idx < this.modes.length ? this.modes[idx] : "";
        }

        @Override
        public boolean isMode(String name) {
            return this.getMode().equalsIgnoreCase(name);
        }

        @Override
        public void setMode(Value mode) {
            if (this.property == null || mode == null) {
                super.setMode(mode);
                return;
            }
            for (int i = 0; i < this.modes.length; i++) {
                if (this.modes[i].equals(mode.getName())) {
                    this.property.setValue(i);
                    return;
                }
            }
        }

        @Override
        public Property<?> getProperty() {
            return this.property;
        }
    }

    public static class ColValue extends ColorValue implements Bound {
        private final ColorProperty property;

        public ColValue(Object parent, ColorProperty property) {
            super(parent, property.getName(), property.getName(), property.getValue(), property.getValue());
            this.property = property;
        }

        @Override
        public int getColor1() {
            return this.property.getValue() & 0xFFFFFF;
        }

        @Override
        public void setColor1(int color) {
            this.property.setValue(color & 0xFFFFFF);
        }

        @Override
        public int getColor2() {
            return this.getColor1();
        }

        @Override
        public void setColor2(int color) {
            this.setColor1(color);
        }

        @Override
        public java.awt.Color getColor(int millis) {
            return new java.awt.Color(this.getColor1());
        }

        @Override
        public Property<?> getProperty() {
            return this.property;
        }
    }

    public static class StrValue extends StringValue implements Bound {
        private final TextProperty property;

        public StrValue(Object parent, TextProperty property) {
            super(parent, property.getName(), property.getName(), property.getValue());
            this.property = property;
        }

        @Override
        public String getValue() {
            String current = this.property.getValue();
            return current == null ? "" : current;
        }

        @Override
        public void setValue(String value) {
            this.property.setValue(value == null ? "" : value);
        }

        @Override
        public Property<?> getProperty() {
            return this.property;
        }
    }
}
