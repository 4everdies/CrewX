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
    private Module module;
    private List<Value> settings = new ArrayList<>();
    private int categoryIndex;
    
    public BridgeModule(Module module, int categoryIndex) {
        this.module = module;
        this.categoryIndex = categoryIndex;
        convertProperties();
    }
    
    private void convertProperties() {
        List<Property<?>> props = MyauBridge.getProperties(module);
        if(props == null) return;
        for(Property<?> prop : props) {
            if(!prop.isVisible()) continue;
            if(prop instanceof BooleanProperty) {
                BooleanProperty bp = (BooleanProperty) prop;
                settings.add(new BooleanValue(this, bp.getName(), bp.getName(), bp.getValue()));
            } else if(prop instanceof FloatProperty) {
                FloatProperty fp = (FloatProperty) prop;
                settings.add(new NumberValue(this, fp.getName(), fp.getValue(), fp.getMinimum(), fp.getMaximum(), 0.01));
            } else if(prop instanceof IntProperty) {
                IntProperty ip = (IntProperty) prop;
                settings.add(new NumberValue(this, ip.getName(), ip.getValue(), ip.getMinimum(), ip.getMaximum(), 1));
            } else if(prop instanceof ModeProperty) {
                ModeProperty mp = (ModeProperty) prop;
                String[] modes = getModes(mp);
                if(modes != null) {
                    Value[] modeValues = new Value[modes.length];
                    for(int i = 0; i < modes.length; i++) {
                        final int idx = i;
                        modeValues[i] = new Value<Object>(this, modes[i], modes[i]) {};
                    }
                    ModeValue mv = new ModeValue(this, mp.getName(), modeValues);
                    if(mp.getValue() >= 0 && mp.getValue() < modes.length) {
                        mv.setMode(modeValues[mp.getValue()]);
                    }
                    settings.add(mv);
                }
            } else if(prop instanceof PercentProperty) {
                PercentProperty pp = (PercentProperty) prop;
                settings.add(new NumberValue(this, pp.getName(), pp.getValue(), pp.getMinimum(), pp.getMaximum(), 1));
            } else if(prop instanceof ColorProperty) {
                ColorProperty cp = (ColorProperty) prop;
                settings.add(new ColorValue(this, cp.getName(), cp.getValue(), cp.getValue()));
            }
        }
    }
    
    private String[] getModes(ModeProperty mp) {
        try {
            Field modesField = ModeProperty.class.getDeclaredField("modes");
            modesField.setAccessible(true);
            return (String[]) modesField.get(mp);
        } catch(Exception e) {
            return null;
        }
    }
    
    public String getName() { return module.getName(); }
    public String getDescription() { return module.getName(); }
    public boolean isEnabled() { return module.isEnabled(); }
    public void toggle() { module.toggle(); }
    public int getCategoryIndex() { return categoryIndex; }
    public List<Value> getSettings() { return settings; }
    public int getKey() { return module.getKey(); }
    public void setKey(int key) { module.setKey(key); }
    public Module getModule() { return module; }
    
    public static String getCategoryName(int index) {
        switch(index) {
            case 0: return "Combat";
            case 1: return "Movement";
            case 2: return "Render";
            case 3: return "Player";
            case 4: return "Misc";
            default: return "Other";
        }
    }
}
