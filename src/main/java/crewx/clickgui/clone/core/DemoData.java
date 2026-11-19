package crewx.clickgui.clone.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Dados de exemplo (nomes iguais aos do screenshot de referência) só para demonstrar o visual.
 * Troque pelos seus módulos reais.
 */
public final class DemoData {
    private DemoData() { }

    /** Teclas do catfont.ttf: a=monitor b=escudo c=engrenagem d=foguete e=pessoa f=olho g=globo. */
    public static List<ClickCategory> categories() {
        List<ClickCategory> l = new ArrayList<ClickCategory>();
        l.add(cat("Combat", "b", "LagRange", "TickBase", "InertiaDelay", "AntiFireball", "TimerRange",
                "AutoBlock", "Backtrack", "MoreKB", "KillAura", "Velocity", "Reduce"));
        l.add(cat("Misc", "c", "ClickFriends", "AntiCrash", "NoCustomPack", "Disabler", "HealthFix", "Notify",
                "AutoGG", "JoinClaim", "InputSounds", "Tweaks", "AutoTool", "HitMarker", "CustomSkin", "AutoRegister"));
        l.add(cat("Movement", "d", "AirStuck", "AutoSprint", "FastLadder", "WallClimb", "Flight",
                "KeepSprint", "Speed", "NoSlow"));
        l.add(cat("Player", "e", "AutoHeal", "BalanceAbuse", "NoFall", "InvMove", "AntiEffect", "FakeLag",
                "AutoArmor", "Manager", "Stealer", "Regen", "AntiAim"));
        l.add(cat("Visual", "f", "Hud", "ESP", "BetterChat", "BetterHotbar", "BetterTab", "DamageEffects",
                "AntiInvisibles", "MoreParticles", "ThirdPerson", "KillEffect", "AspectRatio", "Capes", "ClickGUI",
                "Camera", "Ambience", "Chams", "ChestESP", "ViewModel", "TargetHUD", "Breadcrumbs", "ChinaHat",
                "EnchantGlint", "HitColor", "ItemPhysics", "Waypoints", "NoShadows", "SkeletonESP", "NameProtect"));
        l.add(cat("World", "g", "Scaffold", "FastBreak", "Breaker", "FastPlace"));
        l.add(cat("Client", "a", "ShaderProcessing", "NoDiscordRPC", "ClientSpoofer", "Debug", "Test",
                "CheatDetector", "MediaPlayer", "ToggleSounds", "Targets"));

        // settings de exemplo (os mesmos valores do screenshot)
        module(l, "InertiaDelay")
                .add(new NumberSetting("Delay", 0, 1500, 400, 1))
                .add(new NumberSetting("Lag Chance", 0, 100, 100, 1));
        // um módulo com todos os tipos de setting, para testar a GUI
        module(l, "Hud")
                .add(new BooleanSetting("Watermark", true))
                .add(new ModeSetting("Theme", 0, "Pink", "Ocean", "Mono"))
                .add(new NumberSetting("Scale", 0.5, 2.0, 1.0, 0.1));
        return l;
    }

    public static ClickModule module(List<ClickCategory> cats, String name) {
        for (ClickCategory c : cats)
            for (ClickModule m : c.modules)
                if (m.name.equals(name)) return m;
        throw new IllegalArgumentException(name);
    }

    private static ClickCategory cat(String name, String glyph, String... modules) {
        ClickCategory c = new ClickCategory(name, glyph);
        for (String m : modules) c.modules.add(new ClickModule(m));
        return c;
    }
}
