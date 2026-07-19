package myau.clickgui.value.settings;

import java.util.function.Supplier;
import myau.clickgui.value.Value;

public class RangeValue extends Value {
    public double min, max, minValue, maxValue, increment;
    
    public RangeValue(Object parent, String id, String name, double minValue, double maxValue, double min, double max, double increment) {
        super(parent, id, name); this.min = min; this.max = max; this.minValue = minValue; this.maxValue = maxValue; this.increment = increment;
    }
    public RangeValue(Object parent, String name, double minValue, double maxValue, double min, double max, double increment) {
        super(parent, name); this.min = min; this.max = max; this.minValue = minValue; this.maxValue = maxValue; this.increment = increment;
    }
    public void setMinValue(double value) {
        double precision = 1 / increment;
        this.minValue = Math.min(Math.round(Math.max(min, Math.min(max, value)) * precision) / precision, this.maxValue);
    }
    public void setMaxValue(double value) {
        double precision = 1 / increment;
        this.maxValue = Math.max(this.minValue, Math.round(Math.max(min, Math.min(max, value)) * precision) / precision);
    }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getIncrement() { return increment; }
    public double getMinValue() { return minValue; }
    public double getMaxValue() { return maxValue; }
}
