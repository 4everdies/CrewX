package myau.clickgui.bridge;

import java.util.ArrayList;
import java.util.List;
import myau.clickgui.font.IconUtils;
import myau.clickgui.theme.ThemeManager;

public class BridgeClient {
    private static BridgeClient INSTANCE;
    private ThemeManager themeManager;
    private List<BridgeModule> modules = new ArrayList<>();
    
    public static BridgeClient getInstance() {
        if(INSTANCE == null) INSTANCE = new BridgeClient();
        return INSTANCE;
    }
    
    public void init() {
        themeManager = new ThemeManager();
        IconUtils.init();
    }
    
    public ThemeManager getThemeManager() { return themeManager; }
    public List<BridgeModule> getModules() { return modules; }
    public void setModules(List<BridgeModule> modules) { this.modules = modules; }
    public void addModule(BridgeModule m) { modules.add(m); }
    
    public static int getColor(String name) {
        return getInstance().getThemeManager().get(name);
    }
}
