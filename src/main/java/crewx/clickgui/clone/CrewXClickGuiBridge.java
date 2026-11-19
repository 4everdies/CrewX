package crewx.clickgui.clone;

import crewx.CrewX;
import crewx.clickgui.clone.core.BooleanSetting;
import crewx.clickgui.clone.core.ClickCategory;
import crewx.clickgui.clone.core.ClickGui;
import crewx.clickgui.clone.core.ClickModule;
import crewx.clickgui.clone.core.ModeSetting;
import crewx.clickgui.clone.core.NumberSetting;
import crewx.clickgui.clone.core.TextSetting;
import crewx.clickgui.clone.core.Theme;
import crewx.clickgui.clone.mc.McClickGuiScreen;
import crewx.module.Module;
import crewx.module.ModuleCategory;
import crewx.property.Property;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
import crewx.property.properties.PercentProperty;
import crewx.property.properties.TextProperty;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridge CrewX ↔ ClickGUI clone.
 * {@link #syncFromModules()} atualiza enabled/key/hidden a cada frame
 * para refletir toggles feitos por comando (.t) ou binds.
 */
public final class CrewXClickGuiBridge {
    private static ClickGui gui;
    private static Field modesField;
    /** ClickModule → Module real (para sync). */
    private static final Map<ClickModule, Module> BOUND = new IdentityHashMap<ClickModule, Module>();

    private CrewXClickGuiBridge() { }

    public static synchronized ClickGui getOrCreate() {
        if (gui == null) {
            gui = build();
        }
        return gui;
    }

    public static synchronized void rebuild() {
        BOUND.clear();
        gui = build();
    }

    public static void open() {
        if (CrewX.moduleManager == null) {
            throw new IllegalStateException("moduleManager is null");
        }
        if (CrewX.propertyManager == null) {
            throw new IllegalStateException("propertyManager is null");
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            throw new IllegalStateException("Minecraft is null");
        }
        ClickGui g = getOrCreate();
        syncFromModules();
        mc.displayGuiScreen(new McClickGuiScreen(g, -1));
    }

    /** Copia enabled/key/hidden dos módulos reais → modelo da GUI (sem disparar onToggle). */
    private static boolean skipNumberPull;

    public static void setSkipNumberPull(boolean v) {
        skipNumberPull = v;
    }

    public static void syncFromModules() {
        for (Map.Entry<ClickModule, Module> e : BOUND.entrySet()) {
            ClickModule cm = e.getKey();
            Module mod = e.getValue();
            if (cm == null || mod == null) continue;
            try {
                cm.setEnabledSilently(mod.isEnabled());
                int k = mod.getKey();
                cm.key = (k == 0) ? -1 : k;
                cm.hidden = mod.isHidden();
                // Don't pull numbers while the user is dragging a slider
                if (!skipNumberPull) {
                    for (crewx.clickgui.clone.core.Setting s : cm.settings) {
                        if (s instanceof SyncedNumber) {
                            ((SyncedNumber) s).pullFromProperty();
                        }
                    }
                }
            } catch (Throwable ignored) { }
        }
    }

    private static ClickGui build() {
        BOUND.clear();
        List<ClickCategory> cats = new ArrayList<ClickCategory>();
        for (ModuleCategory cat : ModuleCategory.values()) {
            ClickCategory cc = new ClickCategory(cat.getDisplayName(), glyphFor(cat));
            List<Module> modules;
            try {
                modules = CrewX.moduleManager.getModules(cat);
            } catch (Throwable t) {
                continue;
            }
            if (modules == null) continue;
            for (Module mod : modules) {
                try {
                    cc.modules.add(wrap(mod));
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            if (!cc.modules.isEmpty()) {
                cats.add(cc);
            }
        }
        if (cats.isEmpty()) {
            ClickCategory empty = new ClickCategory("Empty", "a");
            empty.modules.add(new ClickModule("No modules loaded"));
            cats.add(empty);
        }
        return new ClickGui(new Theme(), cats);
    }

    private static String glyphFor(ModuleCategory cat) {
        if (cat == null) return "a";
        switch (cat) {
            case COMBAT: return "b";
            case MOVEMENT: return "d";
            case RENDER: return "f";
            case PLAYER: return "e";
            case MISC: return "c";
            case SCRIPT: return "a";
            default: return "a";
        }
    }

    private static ClickModule wrap(final Module mod) {
        final ClickModule cm = new ClickModule(mod.getName());
        cm.setEnabledSilently(mod.isEnabled());
        int k = mod.getKey();
        cm.key = (k == 0) ? -1 : k;
        cm.hidden = mod.isHidden();
        BOUND.put(cm, mod);

        cm.onToggle(new java.util.function.Consumer<Boolean>() {
            @Override
            public void accept(Boolean on) {
                try {
                    if (mod.isEnabled() != on.booleanValue()) {
                        mod.setEnabled(on.booleanValue());
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
        cm.onKey(new java.util.function.IntConsumer() {
            @Override
            public void accept(int key) {
                try {
                    mod.setKey(key < 0 ? 0 : key);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });

        List<Property<?>> props = null;
        try {
            props = CrewX.propertyManager.properties.get(mod);
        } catch (Throwable ignored) { }

        if (props != null) {
            for (int i = 0; i < props.size(); i++) {
                Property<?> prop = props.get(i);
                try {
                    if (prop == null) continue;
                    // Keep all properties; visibility checked live via Setting.visible().
                    if (prop instanceof BooleanProperty) {
                        cm.add(new SyncedBool(prop.getName(), (BooleanProperty) prop));
                    } else if (prop instanceof ModeProperty) {
                        ModeProperty mp = (ModeProperty) prop;
                        String[] modes = getModes(mp);
                        int idx = 0;
                        try {
                            Integer v = mp.getValue();
                            if (v != null) idx = v.intValue();
                        } catch (Throwable ignored) { }
                        cm.add(new SyncedMode(prop.getName(), idx, modes, mp));
                    } else if (prop instanceof IntProperty) {
                        IntProperty ip = (IntProperty) prop;
                        double min = ip.getMinimum() == null ? 0 : ip.getMinimum().doubleValue();
                        double max = ip.getMaximum() == null ? 100 : ip.getMaximum().doubleValue();
                        if (max < min) { double tmp = min; min = max; max = tmp; }
                        double val = ip.getValue() == null ? min : ip.getValue().doubleValue();
                        cm.add(new SyncedNumber(prop.getName(), min, max, val, 1.0, ip, true));
                    } else if (prop instanceof FloatProperty) {
                        FloatProperty fp = (FloatProperty) prop;
                        double min = fp.getMinimum() == null ? 0 : fp.getMinimum().doubleValue();
                        double max = fp.getMaximum() == null ? 1 : fp.getMaximum().doubleValue();
                        if (max < min) { double tmp = min; min = max; max = tmp; }
                        double val = fp.getValue() == null ? min : fp.getValue().doubleValue();
                        double step = (max - min) <= 2 ? 0.01 : ((max - min) <= 20 ? 0.1 : 0.5);
                        cm.add(new SyncedNumber(prop.getName(), min, max, val, step, fp, false));
                    } else if (prop instanceof PercentProperty) {
                        PercentProperty pp = (PercentProperty) prop;
                        double val = pp.getValue() == null ? 0 : pp.getValue().doubleValue();
                        cm.add(new SyncedNumber(prop.getName(), 0, 100, val, 1.0, pp, true));
                    } else if (prop instanceof TextProperty) {
                        TextProperty tp = (TextProperty) prop;
                        String tv = tp.getValue() == null ? "" : tp.getValue();
                        cm.add(new SyncedText(prop.getName(), tv, tp));
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
        return cm;
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isEmpty()) return "?";
        String[] parts = raw.replace('_', ' ').replace('-', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1));
        }
        return sb.length() == 0 ? raw : sb.toString();
    }

    private static String[] getModes(ModeProperty property) {
        try {
            if (modesField == null) {
                modesField = ModeProperty.class.getDeclaredField("modes");
                modesField.setAccessible(true);
            }
            String[] modes = (String[]) modesField.get(property);
            if (modes != null && modes.length > 0) {
                String[] out = new String[modes.length];
                for (int i = 0; i < modes.length; i++) {
                    out[i] = pretty(modes[i]);
                }
                return out;
            }
        } catch (Throwable ignored) { }
        return new String[]{"Default"};
    }

    /** Lê sempre do property (toggle por comando/config atualiza na GUI). */
    private static final class SyncedBool extends BooleanSetting {
        private final BooleanProperty prop;

        SyncedBool(String name, BooleanProperty prop) {
            super(pretty(name), Boolean.TRUE.equals(prop.getValue()));
            this.prop = prop;
        }

        @Override
        public boolean visible() {
            try { return prop.isVisible(); } catch (Throwable e) { return true; }
        }

        @Override
        public boolean get() {
            try {
                return Boolean.TRUE.equals(prop.getValue());
            } catch (Throwable t) {
                return super.get();
            }
        }

        @Override
        public void set(boolean v) {
            super.set(v);
            try {
                prop.setValue(Boolean.valueOf(v));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private static final class SyncedMode extends ModeSetting {
        private final ModeProperty prop;

        SyncedMode(String name, int index, String[] modes, ModeProperty prop) {
            super(pretty(name), index, modes);
            this.prop = prop;
        }

        @Override
        public boolean visible() {
            try { return prop.isVisible(); } catch (Throwable e) { return true; }
        }

        @Override
        public int getIndex() {
            try {
                Integer v = prop.getValue();
                if (v != null) {
                    int i = v.intValue();
                    if (i >= 0 && i < modes.length) return i;
                }
            } catch (Throwable ignored) { }
            return super.getIndex();
        }

        @Override
        public void setIndex(int i) {
            super.setIndex(i);
            try {
                // Must use super.getIndex() — getIndex() would re-read the OLD property value.
                prop.setValue(Integer.valueOf(super.getIndex()));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        @Override
        public void cycle(int dir) {
            super.cycle(dir);
            try {
                prop.setValue(Integer.valueOf(super.getIndex()));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private static final class SyncedNumber extends NumberSetting {
        private final Property<? extends Number> prop;
        private final boolean asInt;

        @SuppressWarnings({"unchecked", "rawtypes"})
        SyncedNumber(String name, double min, double max, double value, double step,
                     Property<? extends Number> prop, boolean asInt) {
            super(pretty(name), min, max, value, step);
            this.prop = prop;
            this.asInt = asInt;
        }

        @Override
        public boolean visible() {
            try { return prop.isVisible(); } catch (Throwable e) { return true; }
        }

        /**
         * Internal NumberSetting value is the source of truth while the GUI is open.
         * Reading prop here broke dragging: set() called get() which returned the OLD
         * property value and wrote it back, so the slider never moved.
         */
        @Override
        public double get() {
            return super.get();
        }

        /** Refresh internal value from the live module property (on open / external change). */
        void pullFromProperty() {
            try {
                Number n = prop.getValue();
                if (n != null) super.set(n.doubleValue());
            } catch (Throwable ignored) { }
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void set(double v) {
            super.set(v);
            // Use super.get() AFTER clamp/step — never get() while it could re-read prop.
            double actual = super.get();
            try {
                boolean ok;
                if (asInt) {
                    ok = ((Property) prop).setValue(Integer.valueOf((int) Math.round(actual)));
                } else {
                    ok = ((Property) prop).setValue(Float.valueOf((float) actual));
                }
                // If validator rejected, keep property in sync by clamping to what stuck
                if (!ok) {
                    Number n = prop.getValue();
                    if (n != null) super.set(n.doubleValue());
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }


    private static final class SyncedText extends TextSetting {
        private final TextProperty prop;

        SyncedText(String name, String value, TextProperty prop) {
            super(pretty(name), value);
            this.prop = prop;
        }

        @Override
        public boolean visible() {
            try { return prop.isVisible(); } catch (Throwable e) { return true; }
        }

        @Override
        public String get() {
            return super.get();
        }

        @Override
        public void set(String v) {
            super.set(v);
            try {
                prop.setValue(v == null ? "" : v);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        void pullFromProperty() {
            try {
                String v = prop.getValue();
                if (v != null) super.set(v);
            } catch (Throwable ignored) { }
        }
    }
}
