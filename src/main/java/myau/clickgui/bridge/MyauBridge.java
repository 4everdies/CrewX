package myau.clickgui.bridge;

import java.util.List;
import myau.Myau;
import myau.module.Module;
import myau.property.Property;

public class MyauBridge {
    public static List<Property<?>> getProperties(Module module) {
        return Myau.propertyManager.properties.get(module);
    }
}
