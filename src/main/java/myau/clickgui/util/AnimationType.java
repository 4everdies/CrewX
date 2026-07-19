package myau.clickgui.util;

import com.google.common.base.Function;

public enum AnimationType {
    Linear(x -> x),
    EaseInSine(x -> 1 - Math.cos((x * Math.PI) / 2)),
    EaseOutSine(x -> Math.sin((x * Math.PI) / 2)),
    EaseInOutSine(x -> -(Math.cos(Math.PI * x) - 1) / 2),
    EaseInQuad(x -> x * x),
    EaseOutQuad(x -> 1 - (1 - x) * (1 - x)),
    EaseInOutQuad(x -> x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2),
    EaseInCubic(x -> x * x * x),
    EaseOutCubic(x -> 1 - Math.pow(1 - x, 3)),
    EaseInOutBack(x -> x < 0.5
        ? (Math.pow(2 * x, 2) * ((1.70158 * 1.525 + 1) * 2 * x - 1.70158 * 1.525)) / 2
        : (Math.pow(2 * x - 2, 2) * ((1.70158 * 1.525 + 1) * (x * 2 - 2) + 1.70158 * 1.525) + 2) / 2);
    
    private final Function<Double, Double> function;
    AnimationType(final Function<Double, Double> function) { this.function = function; }
    public Function<Double, Double> get() { return function; }
}
