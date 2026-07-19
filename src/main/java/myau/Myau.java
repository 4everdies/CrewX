package myau;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ksyz.accountmanager.AccountManager;
import myau.command.CommandManager;
import myau.command.commands.*;
import myau.config.Config;
import myau.event.EventManager;
import myau.management.*;
import myau.module.Module;
import myau.module.ModuleManager;
import myau.module.modules.*;
import myau.property.Property;
import myau.property.PropertyManager;

import myau.clickgui.bridge.BridgeClient;
import myau.clickgui.bridge.BridgeModule;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

public class Myau {
    public static String clientName = "&7[&bCrewX&7]&r ";
    public static String version;
    public static RotationManager rotationManager;
    public static FloatManager floatManager;
    public static BlinkManager blinkManager;
    public static DelayManager delayManager;
    public static LagManager lagManager;
    public static PlayerStateManager playerStateManager;
    public static FriendManager friendManager;
    public static TargetManager targetManager;
    public static PropertyManager propertyManager;
    public static ModuleManager moduleManager;
    public static CommandManager commandManager;

    public Myau() {
        this.init();
    }

    public void init() {
        rotationManager = new RotationManager();
        floatManager = new FloatManager();
        blinkManager = new BlinkManager();
        delayManager = new DelayManager();
        lagManager = new LagManager();
        playerStateManager = new PlayerStateManager();
        friendManager = new FriendManager();
        targetManager = new TargetManager();
        propertyManager = new PropertyManager();
        moduleManager = new ModuleManager();
        commandManager = new CommandManager();
        EventManager.register(rotationManager);
        EventManager.register(floatManager);
        EventManager.register(blinkManager);
        EventManager.register(delayManager);
        EventManager.register(lagManager);
        EventManager.register(moduleManager);
        EventManager.register(commandManager);
        moduleManager.modules.put(AimAssist.class, new AimAssist());
        moduleManager.modules.put(Backtrack.class, new Backtrack());
        moduleManager.modules.put(Fakelag.class, new Fakelag());
        moduleManager.modules.put(AntiAFK.class, new AntiAFK());
        moduleManager.modules.put(AntiDebuff.class, new AntiDebuff());
        moduleManager.modules.put(AntiFireball.class, new AntiFireball());
        moduleManager.modules.put(AntiObbyTrap.class, new AntiObbyTrap());
        moduleManager.modules.put(AntiObfuscate.class, new AntiObfuscate());
        moduleManager.modules.put(AntiVoid.class, new AntiVoid());
        moduleManager.modules.put(AutoClicker.class, new AutoClicker());
        moduleManager.modules.put(AutoAnduril.class, new AutoAnduril());
        moduleManager.modules.put(AutoHeal.class, new AutoHeal());
        moduleManager.modules.put(AutoTool.class, new AutoTool());
        moduleManager.modules.put(AutoRecraft.class, new AutoRecraft());
        moduleManager.modules.put(AutoRefill.class, new AutoRefill());
        moduleManager.modules.put(AutoPot.class, new AutoPot());
        moduleManager.modules.put(ThrowPot.class, new ThrowPot());
        moduleManager.modules.put(SprintReset.class, new SprintReset());
        moduleManager.modules.put(DynamicIsland.class, new DynamicIsland());
        moduleManager.modules.put(Piercing.class, new Piercing());
        moduleManager.modules.put(Disabler.class, new Disabler());
        moduleManager.modules.put(HackerDetector.class, new HackerDetector());
        moduleManager.modules.put(AutoSoup.class, new AutoSoup());
        moduleManager.modules.put(BedNuker.class, new BedNuker());
        moduleManager.modules.put(BedESP.class, new BedESP());
        moduleManager.modules.put(BedTracker.class, new BedTracker());
        moduleManager.modules.put(Blink.class, new Blink());
        moduleManager.modules.put(Chams.class, new Chams());
        moduleManager.modules.put(ChestESP.class, new ChestESP());
        moduleManager.modules.put(ChestStealer.class, new ChestStealer());
        moduleManager.modules.put(BridgeAssist.class, new BridgeAssist());
        moduleManager.modules.put(KnockbackDelay.class, new KnockbackDelay());
        moduleManager.modules.put(BlockHit.class, new BlockHit());
        moduleManager.modules.put(AutoHeadHitter.class, new AutoHeadHitter());
        moduleManager.modules.put(BedDefender.class, new BedDefender());
        moduleManager.modules.put(Displace.class, new Displace());
        moduleManager.modules.put(Notifications.class, new Notifications());
        moduleManager.modules.put(ESP.class, new ESP());
        moduleManager.modules.put(FastPlace.class, new FastPlace());
        moduleManager.modules.put(Freeze.class, new Freeze());
        moduleManager.modules.put(Fly.class, new Fly());
        moduleManager.modules.put(FullBright.class, new FullBright());
        moduleManager.modules.put(GhostHand.class, new GhostHand());
        moduleManager.modules.put(GuiModule.class, new GuiModule());
        moduleManager.modules.put(HitSelect.class, new HitSelect());
        moduleManager.modules.put(HUD.class, new HUD());
        moduleManager.modules.put(MoreKB.class, new MoreKB());
        moduleManager.modules.put(Indicators.class, new Indicators());
        moduleManager.modules.put(InventoryClicker.class, new InventoryClicker());
        moduleManager.modules.put(InvManager.class, new InvManager());
        moduleManager.modules.put(InvWalk.class, new InvWalk());
        moduleManager.modules.put(ItemESP.class, new ItemESP());
        moduleManager.modules.put(Jesus.class, new Jesus());
        moduleManager.modules.put(KeepSprint.class, new KeepSprint());
        moduleManager.modules.put(HitBox.class, new HitBox());
        moduleManager.modules.put(KillAura.class, new KillAura());
        moduleManager.modules.put(LagRange.class, new LagRange());
        moduleManager.modules.put(LightningTracker.class, new LightningTracker());
        moduleManager.modules.put(LongJump.class, new LongJump());
        moduleManager.modules.put(MCF.class, new MCF());
        moduleManager.modules.put(NameTags.class, new NameTags());
        moduleManager.modules.put(NickHider.class, new NickHider());
        moduleManager.modules.put(NoFall.class, new NoFall());
        moduleManager.modules.put(NoHitDelay.class, new NoHitDelay());
        moduleManager.modules.put(NoHurtCam.class, new NoHurtCam());
        moduleManager.modules.put(NoJumpDelay.class, new NoJumpDelay());
        moduleManager.modules.put(NoRotate.class, new NoRotate());
        moduleManager.modules.put(NoSlow.class, new NoSlow());
        moduleManager.modules.put(Radar.class, new Radar());
        moduleManager.modules.put(Reach.class, new Reach());
        moduleManager.modules.put(SafeWalk.class, new SafeWalk());
        moduleManager.modules.put(Scaffold.class, new Scaffold());
        moduleManager.modules.put(AutoBlockIn.class, new AutoBlockIn());
        moduleManager.modules.put(Spammer.class, new Spammer());
        moduleManager.modules.put(Speed.class, new Speed());
        moduleManager.modules.put(SpeedMine.class, new SpeedMine());
        moduleManager.modules.put(Sprint.class, new Sprint());
        moduleManager.modules.put(TargetHUD.class, new TargetHUD());
        moduleManager.modules.put(TargetStrafe.class, new TargetStrafe());
        moduleManager.modules.put(Tracers.class, new Tracers());
        moduleManager.modules.put(Trajectories.class, new Trajectories());
        moduleManager.modules.put(Velocity.class, new Velocity());
        moduleManager.modules.put(ViewClip.class, new ViewClip());
        moduleManager.modules.put(Wtap.class, new Wtap());
        moduleManager.modules.put(Xray.class, new Xray());
        moduleManager.modules.put(Insults.class, new Insults());
        moduleManager.modules.put(AutoChest.class, new AutoChest());
        moduleManager.modules.put(Cape.class, new Cape());
        moduleManager.modules.put(AntiBot.class, new AntiBot());
        commandManager.commands.add(new BindCommand());
        commandManager.commands.add(new ConfigCommand());
        commandManager.commands.add(new DenickCommand());
        commandManager.commands.add(new FriendCommand());
        commandManager.commands.add(new HelpCommand());
        commandManager.commands.add(new HideCommand());
        commandManager.commands.add(new IgnCommand());
        commandManager.commands.add(new ItemCommand());
        commandManager.commands.add(new ListCommand());
        commandManager.commands.add(new ModuleCommand());
        commandManager.commands.add(new PlayerCommand());
        commandManager.commands.add(new ShowCommand());
        commandManager.commands.add(new TargetCommand());
        commandManager.commands.add(new ToggleCommand());
        commandManager.commands.add(new VclipCommand());
        for (Module module : moduleManager.modules.values()) {
            ArrayList<Property<?>> properties = new ArrayList<>();
            for (final Field field : module.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                final Object obj;
                try {
                    obj = field.get(module);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                if (obj instanceof Property<?>) {
                    ((Property<?>) obj).setOwner(module);
                    properties.add((Property<?>) obj);
                }
            }
            propertyManager.properties.put(module.getClass(), properties);
            EventManager.register(module);
        }
        initClickGui();

        Config config = new Config("default", true);
        if (config.file.exists()) {
            config.load();
        }
        if (friendManager.file.exists()) {
            friendManager.load();
        }
        if (targetManager.file.exists()) {
            targetManager.load();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(config::save));

        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(Myau.class.getResourceAsStream("/version.json")), StandardCharsets.UTF_8)) {
            JsonObject modInfo = new JsonParser().parse(reader).getAsJsonObject();
            version = modInfo.get("version").getAsString();
        } catch (Exception e) {
            version = "dev";
        }

        AccountManager.init();
    }

    private void initClickGui() {
        BridgeClient client = BridgeClient.getInstance();
        client.init();

        ArrayList<BridgeModule> bridgeModules = new ArrayList<>();

        int[] categoryMap = new int[moduleManager.modules.size()];
        int idx = 0;
        for (Module module : moduleManager.modules.values()) {
            categoryMap[idx] = getCategoryForModule(module);
            idx++;
        }

        idx = 0;
        for (Module module : moduleManager.modules.values()) {
            bridgeModules.add(new BridgeModule(module, categoryMap[idx]));
            idx++;
        }

        client.setModules(bridgeModules);
    }

    private int getCategoryForModule(Module module) {
        if (module instanceof myau.module.modules.AimAssist ||
            module instanceof myau.module.modules.Backtrack ||
            module instanceof myau.module.modules.Fakelag ||
            module instanceof myau.module.modules.AutoClicker ||
            module instanceof myau.module.modules.KillAura ||
            module instanceof myau.module.modules.Wtap ||
            module instanceof myau.module.modules.Velocity ||
            module instanceof myau.module.modules.Freeze ||
            module instanceof myau.module.modules.Reach ||
            module instanceof myau.module.modules.TargetStrafe ||
            module instanceof myau.module.modules.NoHitDelay ||
            module instanceof myau.module.modules.AntiFireball ||
            module instanceof myau.module.modules.LagRange ||
            module instanceof myau.module.modules.HitBox ||
            module instanceof myau.module.modules.MoreKB ||
            module instanceof myau.module.modules.HitSelect ||
            module instanceof myau.module.modules.Piercing ||
            module instanceof myau.module.modules.BlockHit ||
            module instanceof myau.module.modules.Displace ||
            module instanceof myau.module.modules.KnockbackDelay ||
            module instanceof myau.module.modules.SprintReset) {
            return 0;
        }
        if (module instanceof myau.module.modules.AntiAFK ||
            module instanceof myau.module.modules.Fly ||
            module instanceof myau.module.modules.Speed ||
            module instanceof myau.module.modules.LongJump ||
            module instanceof myau.module.modules.Sprint ||
            module instanceof myau.module.modules.SafeWalk ||
            module instanceof myau.module.modules.Jesus ||
            module instanceof myau.module.modules.Blink ||
            module instanceof myau.module.modules.NoFall ||
            module instanceof myau.module.modules.NoSlow ||
            module instanceof myau.module.modules.KeepSprint ||
            module instanceof myau.module.modules.BridgeAssist ||
            module instanceof myau.module.modules.NoJumpDelay ||
            module instanceof myau.module.modules.AntiVoid) {
            return 1;
        }
        if (module instanceof myau.module.modules.ESP ||
            module instanceof myau.module.modules.Chams ||
            module instanceof myau.module.modules.FullBright ||
            module instanceof myau.module.modules.Tracers ||
            module instanceof myau.module.modules.NameTags ||
            module instanceof myau.module.modules.Xray ||
            module instanceof myau.module.modules.TargetHUD ||
            module instanceof myau.module.modules.Indicators ||
            module instanceof myau.module.modules.BedESP ||
            module instanceof myau.module.modules.ItemESP ||
            module instanceof myau.module.modules.ViewClip ||
            module instanceof myau.module.modules.NoHurtCam ||
            module instanceof myau.module.modules.HUD ||
            module instanceof myau.module.modules.GuiModule ||
            module instanceof myau.module.modules.ChestESP ||
            module instanceof myau.module.modules.Trajectories ||
            module instanceof myau.module.modules.Radar ||
            module instanceof myau.module.modules.DynamicIsland ||
            module instanceof myau.module.modules.Notifications ||
            module instanceof myau.module.modules.Cape) {
            return 2;
        }
        if (module instanceof myau.module.modules.AutoHeal ||
            module instanceof myau.module.modules.AutoTool ||
            module instanceof myau.module.modules.ChestStealer ||
            module instanceof myau.module.modules.InvManager ||
            module instanceof myau.module.modules.InvWalk ||
            module instanceof myau.module.modules.Scaffold ||
            module instanceof myau.module.modules.AutoBlockIn ||
            module instanceof myau.module.modules.SpeedMine ||
            module instanceof myau.module.modules.FastPlace ||
            module instanceof myau.module.modules.GhostHand ||
            module instanceof myau.module.modules.MCF ||
            module instanceof myau.module.modules.AntiDebuff ||
            module instanceof myau.module.modules.AutoRecraft ||
            module instanceof myau.module.modules.AutoRefill ||
            module instanceof myau.module.modules.AutoSoup ||
            module instanceof myau.module.modules.AutoHeadHitter ||
            module instanceof myau.module.modules.BedDefender ||
            module instanceof myau.module.modules.AutoChest ||
            module instanceof myau.module.modules.AutoPot ||
            module instanceof myau.module.modules.ThrowPot) {
            return 3;
        }
        return 4;
    }
}
