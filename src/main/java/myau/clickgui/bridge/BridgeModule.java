package myau.clickgui.bridge;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import myau.clickgui.value.Value;
import myau.clickgui.value.settings.*;
import myau.module.Module;
import myau.property.Property;
import myau.property.properties.*;

public class BridgeModule {
    private final Module module;
    private final List<Value> settings = new ArrayList<>();
    private final int categoryIndex;

    public BridgeModule(Module module, int categoryIndex) {
        this.module = module;
        this.categoryIndex = categoryIndex;
        convertProperties();
    }

    private void convertProperties() {
        List<Property<?>> props = MyauBridge.getProperties(module);
        if (props == null) return;
        for (Property<?> prop : props) {
            if (prop instanceof BooleanProperty) {
                settings.add(new BoundValues.BoolValue(this, (BooleanProperty) prop));
            } else if (prop instanceof FloatProperty) {
                settings.add(BoundValues.NumValue.of(this, (FloatProperty) prop));
            } else if (prop instanceof PercentProperty) {
                settings.add(BoundValues.NumValue.of(this, (PercentProperty) prop));
            } else if (prop instanceof IntProperty) {
                settings.add(BoundValues.NumValue.of(this, (IntProperty) prop));
            } else if (prop instanceof ModeProperty) {
                ModeProperty mp = (ModeProperty) prop;
                String[] modes = getModes(mp);
                if (modes != null && modes.length > 0) {
                    Value[] modeValues = new Value[modes.length];
                    for (int i = 0; i < modes.length; i++) {
                        modeValues[i] = new Value<Object>(this, modes[i], modes[i]) {
                        };
                    }
                    settings.add(new BoundValues.SelectValue(this, mp, modes, modeValues));
                }
            } else if (prop instanceof ColorProperty) {
                settings.add(new BoundValues.ColValue(this, (ColorProperty) prop));
            } else if (prop instanceof TextProperty) {
                settings.add(new BoundValues.StrValue(this, (TextProperty) prop));
            } else if (prop instanceof ButtonProperty) {
                ButtonProperty bp = (ButtonProperty) prop;
                settings.add(new ActionValue(this, "button:" + bp.getName(), bp.getName(), bp::press));
            }
        }
    }

    private String[] getModes(ModeProperty mp) {
        try {
            Field modesField = ModeProperty.class.getDeclaredField("modes");
            modesField.setAccessible(true);
            return (String[]) modesField.get(mp);
        } catch (Exception e) {
            return null;
        }
    }
    public List<Value> getSettings() {
        return settings;
    }
    public List<Value> getVisibleSettings() {
        List<Value> visible = new ArrayList<>(settings.size());
        for (Value v : settings) {
            if (v instanceof BoundValues.Bound) {
                Property<?> prop = ((BoundValues.Bound) v).getProperty();
                if (prop != null && !prop.isVisible()) continue;
            }
            visible.add(v);
        }
        return visible;
    }

    public String getName() { return module.getName(); }
    public String getDescription() { return module.getName(); }
    public boolean isEnabled() { return module.isEnabled(); }
    public void toggle() { module.toggle(); }
    public int getCategoryIndex() { return categoryIndex; }
    public int getKey() { return module.getKey(); }
    public void setKey(int key) { module.setKey(key); }
    public Module getModule() { return module; }

    public static String getCategoryName(int index) {
        switch (index) {
            case 0: return "Combat";
            case 1: return "Movement";
            case 2: return "Render";
            case 3: return "Player";
            case 4: return "Misc";
            case 5: return "Script";
            default: return "Other";
        }
    }
}
