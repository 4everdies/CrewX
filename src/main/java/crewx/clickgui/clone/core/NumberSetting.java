package crewx.clickgui.clone.core;

import java.util.Locale;

public class NumberSetting extends Setting {
    public final double min, max, step;
    private double value;
    /** Progresso animado exibido (estado de animação, uso interno). */
    float shown = -1f;

    public NumberSetting(String name, double min, double max, double value, double step) {
        super(name);
        this.min = min;
        this.max = max;
        this.step = step;
        set(value);
    }

    public double get() {
        return value;
    }

    public void set(double v) {
        if (step > 0) v = Math.round((v - min) / step) * step + min;
        this.value = Math.max(min, Math.min(max, v));
    }

    public double progress() {
        double v = get();
        return max == min ? 0 : (v - min) / (max - min);
    }

    public String display() {
        double v = get();
        if (step >= 1 && step == Math.floor(step)) return Long.toString(Math.round(v));
        int decimals = step < 0.1 ? 2 : 1;
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    @Override
    float height(Theme t) {
        return t.sliderH;
    }
}
