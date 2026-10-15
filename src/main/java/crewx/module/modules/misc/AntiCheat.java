package crewx.module.modules.misc;

import crewx.CrewX;
import crewx.enums.ChatColors;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.LoadWorldEvent;
import crewx.events.PacketEvent;
import crewx.events.PlayerUpdateEvent;
import crewx.module.Module;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.TextProperty;
import crewx.util.TeamUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockLeaves;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches the other players and warns in chat when one of them moves like a cheat would (AimAssist, AutoClicker,
 * AutoBlock, Blink, Legit Scaffold, NoFall, NoSlow, Scaffold, FastMine, Nuker, Bed Nuker, Protocol). Replaces the old
 * HackerDetector. Block packets arrive on the network thread, so everything they touch is concurrent.
 */
public class AntiCheat extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty flagInterval = new IntProperty("flag-interval-seconds", 10, 0, 60);
    public final BooleanProperty addCheatersAsEnemy = new BooleanProperty("add-cheaters-as-enemy", false);
    public final BooleanProperty autoReport = new BooleanProperty("auto-report", false);
    public final TextProperty reportCommand = new TextProperty("report-command", "/report %player%", this.autoReport::getValue);
    public final BooleanProperty hideChatAlerts = new BooleanProperty("hide-chat-alerts", false);
    public final BooleanProperty notification = new BooleanProperty("notification", true);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", true);
    public final BooleanProperty shouldPing = new BooleanProperty("should-ping", true);

    public final BooleanProperty checkAutoBlock = new BooleanProperty("autoblock", true);
    public final BooleanProperty checkLegitScaffold = new BooleanProperty("legit-scaffold", true);
    public final BooleanProperty checkNoSlow = new BooleanProperty("noslow", true);
    public final BooleanProperty checkNuker = new BooleanProperty("nuker", true);
    public final BooleanProperty checkScaffold = new BooleanProperty("scaffold", true);
    public final BooleanProperty checkAimAssist = new BooleanProperty("aimassist-experimental", false);
    public final BooleanProperty checkAutoClicker = new BooleanProperty("autoclicker-experimental", false);
    public final BooleanProperty checkBlink = new BooleanProperty("blink-experimental", false);
    public final BooleanProperty checkFastMine = new BooleanProperty("fastmine-experimental", false);
    public final BooleanProperty checkNoFall = new BooleanProperty("nofall-experimental", false);
    public final BooleanProperty checkProtocol = new BooleanProperty("protocol-experimental", false);

    private enum Check {
        AutoBlock("AutoBlock"), LegitScaffold("Legit Scaffold"), NoSlow("NoSlow"), Nuker("Nuker"), BedNuker("Bed Nuker"),
        Scaffold("Scaffold"), AimAssist("AimAssist"), AutoClicker("AutoClicker"), Blink("Blink"), FastMine("FastMine"),
        NoFall("NoFall"), Protocol("Protocol");

        final String displayName;

        Check(String displayName) {
            this.displayName = displayName;
        }
    }

    /** What is known about one player, updated every tick. */
    private static final class Tracker {
        Block belowBlock;
        int quickSwings, lastPitchTick, lastSwing, yawTicks, restTicks, sprintTicks, lastRestTicks, lastSprintTicks,
                lastStopUsingTick, lastFallingTicks, lastDeltaYTick, stillTicks, lastBreakAnim = -1, lastSneakTick,
                swingBlockTicks, useSprintTicks, perfectSneaks, fallingTicks, scaffoldTicks, horizontalTicks;
        boolean wasUsing, wasSneaking;
        double maxHorizontal, motionX, motionY, motionZ;
        Vec3 fallStart;

        void update(EntityPlayer p) {
            int n = p.ticksExisted;
            this.motionX = p.posX - p.lastTickPosX;
            this.motionY = p.posY - p.lastTickPosY;
            this.motionZ = p.posZ - p.lastTickPosZ;
            this.belowBlock = mc.theWorld.getBlockState(new BlockPos(MathHelper.floor_double(p.posX),
                    MathHelper.floor_double(p.posY - 1.0), MathHelper.floor_double(p.posZ))).getBlock();
            this.maxHorizontal = Math.max(Math.abs(this.motionX), Math.abs(this.motionZ));
            this.restTicks = this.maxHorizontal == 0.0 && this.motionY == 0.0 ? this.restTicks + 1 : 0;
            this.horizontalTicks = this.maxHorizontal >= 0.07 ? this.horizontalTicks + 1 : 0;
            if (Math.abs(this.motionY) >= 0.1) this.lastDeltaYTick = n;
            this.fallingTicks = this.motionY < 0.0 && this.belowBlock == Blocks.air ? this.fallingTicks + 1 : 0;
            if (p.isSneaking()) this.lastSneakTick = n;
            if (p.rotationPitch != p.prevRotationPitch) this.lastPitchTick = n;
            if (!p.isUsingItem() && this.wasUsing) this.lastStopUsingTick = n;
            this.yawTicks = Math.abs(p.rotationYaw - p.prevRotationYaw) >= 1.0F ? this.yawTicks + 1 : 0;
            this.swingBlockTicks = p.isSwingInProgress && p.isBlocking() ? this.swingBlockTicks + 1 : 0;
            this.sprintTicks = p.isSprinting() ? this.sprintTicks + 1 : 0;
            this.useSprintTicks = p.isSprinting() && p.isUsingItem() && !p.isRiding() && !p.isInWater() ? this.useSprintTicks + 1 : 0;
            ItemStack held = p.getHeldItem();
            if (p.isSwingInProgress && p.rotationPitch >= 65.0F && held != null && held.getItem() instanceof ItemBlock) {
                this.scaffoldTicks = this.horizontalTicks >= 15 ? this.scaffoldTicks + 1 : 0;
                if (p.swingProgressInt == 1) {
                    this.perfectSneaks = !this.wasSneaking && p.isSneaking() ? this.perfectSneaks + 1 : 0;
                }
            } else {
                this.perfectSneaks = 0;
                this.scaffoldTicks = 0;
            }
            if (p.isSwingInProgress && (this.lastBreakAnim == -1 || this.lastBreakAnim == 255)) {
                if (p.swingProgressInt == 0) {
                    this.quickSwings = this.lastSwing == 3 ? this.quickSwings + 1 : 0;
                }
            } else {
                this.quickSwings = 0;
            }
            this.stillTicks = !p.isSneaking() && !p.isSwingInProgress && this.maxHorizontal == 0.0 && this.motionY == 0.0
                    && p.rotationYaw == p.prevRotationYaw && p.rotationPitch == p.prevRotationPitch ? this.stillTicks + 1 : 0;
        }

        void endTick(EntityPlayer p) {
            this.lastRestTicks = this.restTicks;
            this.lastSprintTicks = this.sprintTicks;
            this.lastFallingTicks = this.fallingTicks;
            this.wasSneaking = p.isSneaking();
            this.lastSwing = p.swingProgressInt;
            this.wasUsing = p.isUsingItem();
        }
    }

    private final Map<UUID, Tracker> trackers = new ConcurrentHashMap<UUID, Tracker>();
    private final Map<UUID, Map<Check, Long>> lastFlags = new HashMap<UUID, Map<Check, Long>>();
    private final Map<UUID, S25PacketBlockBreakAnim> breakingPlayers = new ConcurrentHashMap<UUID, S25PacketBlockBreakAnim>();
    private final Map<BlockPos, S25PacketBlockBreakAnim> breakingBlocks = new ConcurrentHashMap<BlockPos, S25PacketBlockBreakAnim>();
    private final HashSet<String> reported = new HashSet<String>();
    /** Flags found on the network thread, shown on the game thread. */
    private final java.util.Queue<Object[]> pendingFlags = new java.util.concurrent.ConcurrentLinkedQueue<Object[]>();
    private volatile long lastPacket;
    private long lastPing;

    public AntiCheat() {
        super("AntiCheat", false);
    }

    private boolean enabled(Check check) {
        switch (check) {
            case AutoBlock: return this.checkAutoBlock.getValue();
            case LegitScaffold: return this.checkLegitScaffold.getValue();
            case NoSlow: return this.checkNoSlow.getValue();
            case Nuker:
            case BedNuker: return this.checkNuker.getValue();
            case Scaffold: return this.checkScaffold.getValue();
            case AimAssist: return this.checkAimAssist.getValue();
            case AutoClicker: return this.checkAutoClicker.getValue();
            case Blink: return this.checkBlink.getValue();
            case FastMine: return this.checkFastMine.getValue();
            case NoFall: return this.checkNoFall.getValue();
            case Protocol: return this.checkProtocol.getValue();
            default: return false;
        }
    }

    private boolean active() {
        return this.isEnabled() && mc.theWorld != null && mc.thePlayer != null && !mc.isSingleplayer();
    }

    private boolean ignored(EntityPlayer p) {
        if (p == null || p == mc.thePlayer || p.deathTime != 0) return true;
        AntiBot antiBot = (AntiBot) CrewX.moduleManager.modules.get(AntiBot.class);
        return antiBot != null && antiBot.isEnabled() && antiBot.isBot(p) && !TeamUtil.isInTabList(p);
    }

    private static boolean adventure() {
        return mc.playerController != null && mc.playerController.getCurrentGameType() == WorldSettings.GameType.ADVENTURE;
    }

    private static Block block(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock();
    }

    /** A block with air next to it could be broken by hand; Nuker is only about blocks broken through others. */
    private static boolean exposed(BlockPos pos) {
        for (EnumFacing facing : EnumFacing.values()) {
            if (block(pos.offset(facing)) instanceof BlockAir) return true;
        }
        return false;
    }

    private void check(EntityPlayer p, Tracker t) {
        int n = p.ticksExisted;
        boolean adventure = adventure();
        if (enabled(Check.AimAssist) && p.rotationPitch > -80.0F && p.rotationPitch < 80.0F && t.yawTicks >= 25 && n - t.lastPitchTick >= 25) {
            flag(p, Check.AimAssist, t.yawTicks + " " + (n - t.lastPitchTick));
            return;
        }
        ItemStack held = p.getHeldItem();
        if (enabled(Check.AutoClicker) && !adventure && t.quickSwings >= 10 && mc.thePlayer.getDistanceSqToEntity(p) < 700.0
                && (held == null || held.getItem() != Items.fishing_rod)) {
            flag(p, Check.AutoClicker, String.valueOf(t.quickSwings));
            return;
        }
        if (enabled(Check.AutoBlock) && t.swingBlockTicks >= 15) {
            flag(p, Check.AutoBlock, String.valueOf(t.swingBlockTicks));
            return;
        }
        if (enabled(Check.Blink) && !adventure && t.lastSprintTicks >= 2 && t.lastRestTicks >= 6 && t.maxHorizontal > 0.5
                && t.maxHorizontal < 3.0 && System.currentTimeMillis() - this.lastPacket < 100L) {
            flag(p, Check.Blink, t.lastSprintTicks + " " + t.lastRestTicks + " " + round(t.maxHorizontal));
            return;
        }
        if (enabled(Check.LegitScaffold) && t.perfectSneaks >= 3) {
            flag(p, Check.LegitScaffold, String.valueOf(t.perfectSneaks));
            return;
        }
        if (enabled(Check.NoFall)) {
            if (t.stillTicks == 10 && t.belowBlock == Blocks.air) {
                t.fallStart = new Vec3(p.posX, p.posY, p.posZ);
            }
            if (t.fallStart != null) {
                if (p.hurtTime != 0) {
                    t.fallStart = null;
                } else if (t.lastFallingTicks > 0 && t.lastFallingTicks < 6 && t.belowBlock != Blocks.air
                        && (held == null || held.getItem() != Item.getItemFromBlock(Blocks.ladder))) {
                    double dx = p.posX - t.fallStart.xCoord;
                    double dz = p.posZ - t.fallStart.zCoord;
                    double dy = p.posY - t.fallStart.yCoord;
                    t.fallStart = null;
                    if (dx * dx + dz * dz < 144.0 && dy <= -5.0) {
                        flag(p, Check.NoFall, round(dy) + "m");
                        return;
                    }
                }
            }
        }
        if (enabled(Check.NoSlow) && t.useSprintTicks >= 11 && t.maxHorizontal >= 0.08 && n - t.lastStopUsingTick >= 35) {
            flag(p, Check.NoSlow, t.useSprintTicks + " " + (n - t.lastStopUsingTick));
            return;
        }
        if (enabled(Check.Scaffold) && t.scaffoldTicks > 0 && n - t.lastSneakTick >= 25
                && (n - t.lastDeltaYTick >= 20 || t.scaffoldTicks > 25)) {
            boolean air = true;
            BlockPos pos = p.getPosition().down(2);
            for (int i = 0; i < 4; ++i) {
                if (!(block(pos) instanceof BlockAir)) {
                    air = false;
                    break;
                }
                pos = pos.down();
            }
            if (air) {
                flag(p, Check.Scaffold, t.scaffoldTicks + " " + (n - t.lastSneakTick));
                return;
            }
        }
        if (enabled(Check.Protocol) && Math.abs(p.rotationPitch) >= 92.0F) {
            flag(p, Check.Protocol, "pitch " + round(p.rotationPitch));
        }
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!this.active()) return;
        Object[] pending;
        while ((pending = this.pendingFlags.poll()) != null) {
            flag((EntityPlayer) pending[0], (Check) pending[1], (String) pending[2]);
        }
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (ignored(p)) continue;
            Tracker t = this.trackers.get(p.getUniqueID());
            if (t == null) {
                t = new Tracker();
                this.trackers.put(p.getUniqueID(), t);
            }
            t.update(p);
            check(p, t);
            t.endTick(p);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || !this.active()) return;
        this.lastPacket = System.currentTimeMillis();
        try {
            handlePacket(event.getPacket());
        } catch (RuntimeException ignored) {
            // the world can change under the network thread; a missed flag is better than a broken connection
        }
    }

    private void handlePacket(Object packet) {
        if (packet instanceof S25PacketBlockBreakAnim && (enabled(Check.AutoClicker) || enabled(Check.FastMine))) {
            S25PacketBlockBreakAnim anim = (S25PacketBlockBreakAnim) packet;
            Entity entity = mc.theWorld.getEntityByID(anim.getBreakerId());
            if (entity instanceof EntityPlayer) {
                Tracker t = this.trackers.get(entity.getUniqueID());
                if (t != null) t.lastBreakAnim = anim.getProgress();
                if (enabled(Check.FastMine)) {
                    S25PacketBlockBreakAnim previous = this.breakingPlayers.get(entity.getUniqueID());
                    if (previous != null && anim.getProgress() == 255) {
                        this.breakingBlocks.put(anim.getPosition(), previous);
                    }
                    this.breakingPlayers.put(entity.getUniqueID(), anim);
                }
            }
        }
        if (packet instanceof S23PacketBlockChange) {
            S23PacketBlockChange change = (S23PacketBlockChange) packet;
            if (!(change.getBlockState().getBlock() instanceof BlockAir)) return;
            BlockPos pos = change.getBlockPosition();
            Block block = block(pos);
            if (block == Blocks.air || block == Blocks.bed || block == Blocks.ladder || block instanceof BlockBush || block instanceof BlockLeaves) {
                return;
            }
            EntityPlayer breaker = null;
            S25PacketBlockBreakAnim anim = this.breakingBlocks.remove(pos);
            if (anim != null) {
                Entity entity = mc.theWorld.getEntityByID(anim.getBreakerId());
                if (entity instanceof EntityPlayer) {
                    breaker = (EntityPlayer) entity;
                    if (anim.getProgress() > 4 && anim.getProgress() < 10) {
                        queueFlag(breaker, Check.FastMine, String.valueOf(anim.getProgress()));
                    }
                }
            }
            if (enabled(Check.Nuker)) {
                if (exposed(pos)) return;
                double best = -1.0;
                if (breaker == null && !(bedNukerOn() && mc.thePlayer.getDistanceSq(pos) <= 36.0)) {
                    for (EntityPlayer other : mc.theWorld.playerEntities) {
                        if (ignored(other)) continue;
                        double distance = other.getDistanceSq(pos);
                        if (best != -1.0 && distance >= best) continue;
                        best = distance;
                        breaker = other;
                    }
                }
                if (breaker != null) {
                    queueFlag(breaker, Check.Nuker, block.getLocalizedName() + " " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                }
            }
        } else if (packet instanceof S22PacketMultiBlockChange && enabled(Check.Nuker)) {
            S22PacketMultiBlockChange change = (S22PacketMultiBlockChange) packet;
            S22PacketMultiBlockChange.BlockUpdateData[] blocks = change.getChangedBlocks();
            if (blocks.length != 2 || !(blocks[0].getBlockState().getBlock() instanceof BlockAir)) return;
            BlockPos a = blocks[0].getPos();
            BlockPos b = blocks[1].getPos();
            if (exposed(a) || exposed(b)) return;
            if (bedNukerOn() && (mc.thePlayer.getDistanceSq(a) <= 36.0 || mc.thePlayer.getDistanceSq(b) <= 36.0)) return;
            for (EntityPlayer other : mc.theWorld.playerEntities) {
                if (ignored(other) || other.getDistanceSq(a) > 36.0 && other.getDistanceSq(b) > 36.0) continue;
                queueFlag(other, Check.BedNuker, "");
                break;
            }
        }
    }

    private static boolean bedNukerOn() {
        Module bedNuker = CrewX.moduleManager.modules.get(BedNuker.class);
        return bedNuker != null && bedNuker.isEnabled();
    }

    private void queueFlag(EntityPlayer p, Check check, String info) {
        this.pendingFlags.add(new Object[]{p, check, info});
    }

    private void flag(EntityPlayer p, Check check, String info) {
        if (p == null || mc.thePlayer == null) return;
        String name = p.getName();
        if (CrewX.friendManager.isFriend(name) || this.ignoreTeammates.getValue() && TeamUtil.isSameTeam(p)) return;
        long now = System.currentTimeMillis();
        if (this.flagInterval.getValue() > 0) {
            Map<Check, Long> flags = this.lastFlags.get(p.getUniqueID());
            if (flags == null) {
                flags = new HashMap<Check, Long>();
                this.lastFlags.put(p.getUniqueID(), flags);
            }
            Long last = flags.get(check);
            if (last != null && Math.abs(now - last) <= this.flagInterval.getValue() * 1000L) return;
            flags.put(check, now);
        }
        if (this.addCheatersAsEnemy.getValue() && CrewX.targetManager != null) {
            CrewX.targetManager.add(name);
        }
        String plainName = name.replaceAll("§[0-9a-fk-or]", "");
        String report = this.reportCommand.getValue().replace("%player%", plainName);
        if (this.autoReport.getValue() && !this.reported.contains(plainName) && mc.currentScreen == null) {
            this.reported.add(plainName);
            mc.thePlayer.sendChatMessage(report);
        }
        if (!this.hideChatAlerts.getValue()) {
            double distance = mc.thePlayer.getDistanceToEntity(p);
            ChatComponentText text = new ChatComponentText(ChatColors.formatColor(CrewX.clientName
                    + p.getDisplayName().getFormattedText() + " &7detected for &d" + check.displayName
                    + (info.isEmpty() ? "" : " &8" + info) + " &7(" + round(distance) + "m)"));
            ChatStyle style = new ChatStyle();
            style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(ChatColors.formatColor("&7" + report))));
            style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, report));
            text.appendSibling(new ChatComponentText(ChatColors.formatColor(" &7[&cReport&7]")).setChatStyle(style));
            mc.thePlayer.addChatMessage(text);
        }
        if (this.notification.getValue()) {
            try {
                crewx.module.modules.render.Notifications.pushRaw("AntiCheat", plainName + " - " + check.displayName);
            } catch (Throwable ignored) {
            }
        }
        if (this.shouldPing.getValue() && now - this.lastPing > 1500L) {
            mc.thePlayer.playSound("note.pling", 1.0F, 1.0F);
            this.lastPing = now;
        }
    }

    private static String round(double value) {
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    private void reset() {
        this.trackers.clear();
        this.lastFlags.clear();
        this.breakingPlayers.clear();
        this.breakingBlocks.clear();
        this.reported.clear();
        this.pendingFlags.clear();
        this.lastPing = 0L;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        reset();
    }

    @Override
    public void onDisabled() {
        reset();
    }
}
