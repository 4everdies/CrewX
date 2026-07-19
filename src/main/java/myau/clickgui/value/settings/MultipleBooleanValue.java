package myau.clickgui.value.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import myau.clickgui.value.Value;

public class MultipleBooleanValue extends Value {
    private ArrayList<StringBooleanPair> values = new ArrayList<>();
    
    @SafeVarargs
    public MultipleBooleanValue(Object parent, String id, String name, Pair<String, Boolean>... values) {
        super(parent, id, name);
        for(Pair<String, Boolean> p : values) this.values.add(new StringBooleanPair(p.getLeft(), p.getRight()));
    }
    
    public ArrayList<StringBooleanPair> getValues() { return values; }
    public boolean get(String name) {
        return values.stream().filter(v -> v.name.toLowerCase().equals(name.toLowerCase())).findFirst().orElse(null) != null
                && values.stream().filter(v -> v.name.toLowerCase().equals(name.toLowerCase())).findFirst().get().enabled;
    }
    public List<StringBooleanPair> getEnabledValues() { return values.stream().filter(v -> v.enabled).collect(Collectors.toList()); }
    
    public static class StringBooleanPair {
        public String name;
        public boolean enabled;
        public StringBooleanPair(String name, boolean enabled) { this.name = name; this.enabled = enabled; }
        public String getFirst() { return name; }
        public Boolean getSecond() { return enabled; }
        public void setSecond(boolean val) { this.enabled = val; }
    }
    
    public static class Pair<L, R> {
        private final L left; private final R right;
        public Pair(L left, R right) { this.left = left; this.right = right; }
        public L getLeft() { return left; }
        public R getRight() { return right; }
    }
}
