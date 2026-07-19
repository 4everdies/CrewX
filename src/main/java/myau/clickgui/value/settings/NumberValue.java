package myau.clickgui.value.settings;

import java.util.function.Supplier;
import myau.clickgui.value.Value;

public class NumberValue extends Value {
    private double value, min, max, increment;
    
    public NumberValue(Object parent, String id, String name, double value, double min, double max, double increment) {
        super(parent, id, name); this.value = value; this.min = min; this.max = max; this.increment = increment;
    }
    public NumberValue(Object parent, String name, double value, double min, double max, double increment) {
        super(parent, name); this.value = value; this.min = min; this.max = max; this.increment = increment;
    }
    public double getValue() { return value; }
    public void setValue(double value) {
        double precision = 1 / increment;
        this.value = Math.round(Math.max(min, Math.min(max, value)) * precision) / precision;
    }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getIncrement() { return increment; }
}
