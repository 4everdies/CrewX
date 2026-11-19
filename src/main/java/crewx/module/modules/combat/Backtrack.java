package crewx.module.modules.combat;

/*
 * Ported from OpenAugustus "BackTrack" (augustus.modules.latency.BackTrack).
 * The AugustUS version targets its own client framework; this rewrite keeps the
 * five modes and the option set but uses the CrewX events/settings/managers.
 */

import crewx.CrewX;
import crewx.event.EventManager;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.AttackEvent;
import crewx.events.LoadWorldEvent;
import crewx.events.PacketEvent;
import crewx.events.Render3DEvent;
import crewx.events.TickEvent;
import crewx.mixin.IAccessorRenderManager;
import crewx.module.Module;
import crewx.module.modules.misc.AntiBot;
import crewx.module.modules.player.Scaffold;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ColorProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
import crewx.util.GlStateSync;
import crewx.util.PacketUtil;
import crewx.util.RenderUtil;
import crewx.util.RotationUtil;
import crewx.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.network.play.server.S48PacketResourcePackSend;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Backtrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long TARGET_TIMEOUT_MS = 1000L;
    private static final long POSITION_STALE_MS = 500L;
    private static final int MAX_QUEUED_PACKETS = 4096;

    public final ModeProperty mode = new ModeProperty("mode", 0,
            new String[]{"BackTrack", "LagTrack", "Gomme", "Ping", "ClassicBackTrack"});

    /* ---------------------------------------------------------------- BackTrack */

    public final FloatProperty minHitRange = new FloatProperty("min-hit-range", 3.0F, 0.0F, 10.0F, this::isBackTrack);
    public final FloatProperty maxHitRange = new FloatProperty("max-hit-range", 6.0F, 0.0F, 10.0F);
    public final FloatProperty threshold = new FloatProperty("threshold", 0.125F, 0.0F, 2.0F);
    public final BooleanProperty predict = new BooleanProperty("predict", true, this::isBackTrack);
    public final IntProperty backtrackTimeMin = new IntProperty("backtrack-time-min", 200, 0, 5000, this::isBackTrack);
    public final IntProperty backtrackTimeMax = new IntProperty("backtrack-time-max", 250, 0, 5000, this::isBackTrack);
    public final IntProperty backtrackDelayMin = new IntProperty("backtrack-delay-min", 50, 0, 5000, this::isBackTrack);
    public final IntProperty backtrackDelayMax = new IntProperty("backtrack-delay-max", 150, 0, 5000, this::isBackTrack);
    public final BooleanProperty calculateWithOwnPing = new BooleanProperty("calculate-with-own-ping", false, this::isBackTrack);
    public final BooleanProperty dynamic = new BooleanProperty("dynamic", false, this::isBackTrack);
    public final BooleanProperty forceDelay = new BooleanProperty("force-delay", false, this::isBackTrack);

    /* ------------------------------------------------------------------- Target */

    public final BooleanProperty onlyKillAura = new BooleanProperty("only-killaura", true);
    public final FloatProperty preAimRange = new FloatProperty("pre-aim-range", 4.0F, 0.0F, 15.0F, () -> !this.onlyKillAura.getValue());
    public final BooleanProperty targetPlayers = new BooleanProperty("target-players", true, () -> !this.onlyKillAura.getValue());
    public final BooleanProperty targetMobs = new BooleanProperty("target-mobs", false, () -> !this.onlyKillAura.getValue());
    public final BooleanProperty targetAnimals = new BooleanProperty("target-animals", false, () -> !this.onlyKillAura.getValue());
    public final BooleanProperty targetVillagers = new BooleanProperty("target-villagers", false, () -> !this.onlyKillAura.getValue());
    public final BooleanProperty targetArmorStands = new BooleanProperty("target-armor-stands", false, () -> !this.onlyKillAura.getValue());
    public final BooleanProperty teams = new BooleanProperty("teams", false);
    public final BooleanProperty notWhileScaffold = new BooleanProperty("not-while-scaffold", true);
    public final BooleanProperty clickCheck = new BooleanProperty("click-check", false);
    public final BooleanProperty checkStartDistance = new BooleanProperty("check-start-distance", false);
    public final FloatProperty startDistance = new FloatProperty("start-distance", 3.0F, 0.0F, 6.0F, () -> this.checkStartDistance.getValue());

    /* -------------------------------------------------------------------- Abort */

    public final ModeProperty abortCriterion = new ModeProperty("abort-criterion", 0, new String[]{"None", "OnAttack", "OnRange"},
            () -> !this.isClassic());
    public final FloatProperty stopOnAttackRange = new FloatProperty("stop-on-attack-range", 2.95F, 1.0F, 6.0F,
            () -> "OnRange".equals(this.abortCriterion.getModeString()) && !this.isClassic());
    public final IntProperty stopOnAttackMaxHurtTime = new IntProperty("stop-on-attack-max-hurt-time", 3, 0, 10,
            () -> !"None".equals(this.abortCriterion.getModeString()) && !this.isClassic());
    public final BooleanProperty fixPacketOrder = new BooleanProperty("fix-packet-order", true,
            () -> "OnAttack".equals(this.abortCriterion.getModeString()) && !this.isClassic());
    public final BooleanProperty stopBacktrackAtHurtTime = new BooleanProperty("stop-backtrack-at-hurt-time", false,
            () -> !this.isClassic());
    public final IntProperty continueWithBacktrackAtHurtTime = new IntProperty("continue-with-backtrack-at-hurt-time", 0, 0, 10,
            () -> this.stopBacktrackAtHurtTime.getValue() && !this.isClassic());

    /* ----------------------------------------------------------------- LagTrack */

    public final IntProperty maxLagPackets = new IntProperty("max-lag-packets", 2, 0, 10, this::isLagTrack);
    public final IntProperty delayBetweenLags = new IntProperty("delay-between-lags", 0, 0, 4000, this::isLagTrack);
    public final ModeProperty countMode = new ModeProperty("count-mode", 0, new String[]{"Tick", "MovePacket"}, this::isLagTrack);
    public final BooleanProperty calculateWithOwnPingLagTrack = new BooleanProperty("calculate-with-own-ping-lag-track", false, this::isLagTrack);

    /* -------------------------------------------------------------------- Gomme */

    public final IntProperty lagPackets = new IntProperty("lag-packets", 1, 0, 10, this::isGomme);

    /* --------------------------------------------------------------------- Ping */

    public final IntProperty pingDelayMin = new IntProperty("ping-delay-min", 150, 0, 2000, this::isPing);
    public final IntProperty pingDelayMax = new IntProperty("ping-delay-max", 150, 0, 2000, this::isPing);
    public final IntProperty delayBetweenBacktrackPing = new IntProperty("delay-between-backtrack-ping", 1200, 0, 5000, this::isPing);
    public final BooleanProperty calculateWithOwnPingPing = new BooleanProperty("calculate-with-own-ping-ping", false, this::isPing);
    public final BooleanProperty dynamicPing = new BooleanProperty("dynamic-ping", false, this::isPing);

    /* ---------------------------------------------------------- ClassicBackTrack */

    public final IntProperty classicBacktrackDelayMin = new IntProperty("classic-backtrack-delay-min", 200, 0, 5000, this::isClassic);
    public final IntProperty classicBacktrackDelayMax = new IntProperty("classic-backtrack-delay-max", 250, 0, 5000, this::isClassic);
    public final BooleanProperty dynamicClassic = new BooleanProperty("dynamic-classic", false, this::isClassic);
    public final BooleanProperty onlyWhenNeed = new BooleanProperty("only-when-need", false, this::isClassic);

    /* ---------------------------------------------------------------------- ESP */

    public final ModeProperty targetEsp = new ModeProperty("target-esp", 1, new String[]{"None", "Classic", "PlayerModel", "Wireframe"});
    public final ColorProperty targetColor = new ColorProperty("target-color", 0x00FF00,
            () -> !"None".equals(this.targetEsp.getModeString()));
    public final FloatProperty targetLineWidth = new FloatProperty("target-line-width", 3.0F, 0.5F, 5.0F,
            () -> "Classic".equals(this.targetEsp.getModeString()));
    public final BooleanProperty smoothRendering = new BooleanProperty("smooth-rendering", false,
            () -> !"None".equals(this.targetEsp.getModeString()));
    public final ModeProperty playerEsp = new ModeProperty("player-esp", 1, new String[]{"None", "Classic", "PlayerModel"},
            () -> !this.isBackTrack());
    public final BooleanProperty onlyInThirdPerson = new BooleanProperty("only-in-third-person", true,
            () -> !this.isBackTrack() && !"None".equals(this.playerEsp.getModeString()));
    public final ColorProperty playerColor = new ColorProperty("player-color", 0x00FF00,
            () -> !this.isBackTrack() && !"None".equals(this.playerEsp.getModeString()));
    public final FloatProperty playerLineWidth = new FloatProperty("player-line-width", 3.0F, 0.5F, 5.0F,
            () -> !this.isBackTrack() && "Classic".equals(this.playerEsp.getModeString()));

    /* -------------------------------------------------------------------- State */

    private final Map<Integer, TrackedPos> serverPositions = new ConcurrentHashMap<Integer, TrackedPos>();
    private final Object holdLock = new Object();
    private final ArrayDeque<Packet<?>> incomingQueue = new ArrayDeque<Packet<?>>();
    private final ArrayDeque<Packet<?>> outgoingQueue = new ArrayDeque<Packet<?>>();

    private volatile EntityLivingBase target;
    private volatile long lastAttack;
    private volatile boolean acceptIncoming;
    private volatile long windowDeadline;
    private boolean replaying;

    private long lastWindowEnd;
    private long nextPingWindow;
    private long windowDuration;
    private boolean windowOpen;
    private boolean attackedSinceWindow;
    private boolean hurtTimeBlocked;
    private double lastServerDistance;
    private double lastClientDistance;

    private boolean lagActive;
    private int lagTicks;
    private int lagMovePackets;
    private long lastLagEnd;

    private boolean holdingOutgoing;
    private volatile boolean flushingOutgoing;
    private long outgoingHoldUntil;
    private long nextOutgoingHold;
    private Vec3 lastSentPosition;

    public Backtrack() {
        super("Backtrack", false);
    }

    /* -------------------------------------------------------------- Comparisons */

    private boolean isBackTrack() {
        return "BackTrack".equals(this.mode.getModeString());
    }

    private boolean isLagTrack() {
        return "LagTrack".equals(this.mode.getModeString());
    }

    private boolean isGomme() {
        return "Gomme".equals(this.mode.getModeString());
    }

    private boolean isPing() {
        return "Ping".equals(this.mode.getModeString());
    }

    private boolean isClassic() {
        return "ClassicBackTrack".equals(this.mode.getModeString());
    }

    private boolean isIncomingMode() {
        return this.isBackTrack() || this.isPing();
    }

    private boolean isOutgoingMode() {
        return this.isLagTrack() || this.isGomme() || this.isClassic();
    }

    /* --------------------------------------------------------------- Lifecycle */

    @Override
    public void onEnabled() {
        this.reset(true);
    }

    @Override
    public void onDisabled() {
        this.reset(true);
    }

    @Override
    public void verifyValue(String name) {
        if ("mode".equals(name) && this.isEnabled()) {
            this.reset(true);
        }
    }

    private void reset(boolean processPackets) {
        boolean usedLagTrack = this.lagActive;
        this.target = null;
        this.lastAttack = 0L;
        this.windowOpen = false;
        this.windowDeadline = 0L;
        this.lastWindowEnd = 0L;
        this.nextPingWindow = 0L;
        this.windowDuration = 0L;
        this.attackedSinceWindow = false;
        this.hurtTimeBlocked = false;
        this.lastServerDistance = 0.0D;
        this.lastClientDistance = 0.0D;
        this.acceptIncoming = false;
        this.lagActive = false;
        this.lagTicks = 0;
        this.lagMovePackets = 0;
        this.lastLagEnd = 0L;
        this.holdingOutgoing = false;
        this.outgoingHoldUntil = 0L;
        this.nextOutgoingHold = 0L;
        this.lastSentPosition = null;
        this.serverPositions.clear();
        if (CrewX.lagManager != null && usedLagTrack) {
            CrewX.lagManager.setDelay(0);
        }
        if (processPackets) {
            this.flushIncoming();
            this.flushOutgoing();
        } else {
            synchronized (this.holdLock) {
                this.incomingQueue.clear();
                this.outgoingQueue.clear();
            }
        }
    }

    /* ------------------------------------------------------------------ Events */

    @EventTarget(Priority.HIGHEST)
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || event.isCancelled() || !(event.getTarget() instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase attacked = (EntityLivingBase) event.getTarget();
        if (!this.isValidTarget(attacked)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.target != attacked) {
            this.closeWindow();
            this.target = attacked;
            this.seedPosition(attacked, now);
        }
        this.lastAttack = now;

        if ((this.windowOpen || this.holdingOutgoing || this.lagActive)
                && "OnAttack".equals(this.abortCriterion.getModeString())
                && !this.isClassic()
                && attacked.hurtTime <= this.stopOnAttackMaxHurtTime.getValue()) {
            this.attackedSinceWindow = true;
            if (this.fixPacketOrder.getValue()) {
                this.closeWindow();
            }
        }

        if (this.isIncomingMode() && this.serverPositions.get(attacked.getEntityId()) == null) {
            this.seedPosition(attacked, now);
        }

        if (this.isBackTrack()) {
            this.tryStartBacktrack(now, false);
        } else if (this.isPing()) {
            this.tryStartPing(now);
        } else if (this.isLagTrack()) {
            this.lagTicks = 0;
            this.lagMovePackets = 0;
        } else if (this.isGomme()) {
            this.startOutgoingHold(now);
        }
    }

    @EventTarget(Priority.LOW)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || this.replaying) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null
                || mc.isSingleplayer() || mc.getCurrentServerData() == null) {
            this.reset(true);
            return;
        }

        Packet<?> packet = event.getPacket();
        if (this.isIgnoredPacket(packet)) {
            return;
        }
        if (packet instanceof S06PacketUpdateHealth && ((S06PacketUpdateHealth) packet).getHealth() <= 0.0F) {
            this.endIncomingWindow();
            return;
        }
        EntityLivingBase currentTarget = this.target;
        if (currentTarget != null) {
            if (packet instanceof S13PacketDestroyEntities) {
                for (int entityId : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                    if (entityId == currentTarget.getEntityId()) {
                        this.serverPositions.remove(entityId);
                        this.endIncomingWindow();
                        return;
                    }
                }
            }
            if (packet instanceof S1CPacketEntityMetadata && this.isTargetDead((S1CPacketEntityMetadata) packet, currentTarget)) {
                this.endIncomingWindow();
                return;
            }
            if (packet instanceof S19PacketEntityStatus
                    && ((S19PacketEntityStatus) packet).getEntity(mc.theWorld) == currentTarget) {
                return;
            }
        }

        this.updateServerPosition(packet);
        if (this.isIgnoredPacket(packet)) {
            return;
        }

        synchronized (this.holdLock) {
            if ((this.acceptIncoming && System.currentTimeMillis() < this.windowDeadline) || !this.incomingQueue.isEmpty()) {
                if (this.incomingQueue.size() < MAX_QUEUED_PACKETS) {
                    this.incomingQueue.add(packet);
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onSendPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }
        Packet<?> packet = event.getPacket();

        if (!this.flushingOutgoing && packet instanceof C03PacketPlayer) {
            C03PacketPlayer movement = (C03PacketPlayer) packet;
            if (movement.isMoving()) {
                this.lastSentPosition = new Vec3(movement.getPositionX(), movement.getPositionY(), movement.getPositionZ());
            }
        }

        if (!this.isOutgoingMode() || this.flushingOutgoing) {
            if (this.isLagTrack() && this.lagActive && !this.flushingOutgoing
                    && "MovePacket".equals(this.countMode.getModeString()) && packet instanceof C03PacketPlayer) {
                this.lagMovePackets++;
            }
            return;
        }

        if (!this.holdingOutgoing || this.isIgnoredOutgoing(packet)) {
            return;
        }

        synchronized (this.holdLock) {
            if (this.outgoingQueue.size() < MAX_QUEUED_PACKETS) {
                this.outgoingQueue.add(packet);
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        long now = System.currentTimeMillis();
        this.updateTarget(now);
        this.updateHurtTimeState();

        if (this.target == null || !this.isValidTarget(this.target)) {
            this.endIncomingWindow();
            this.stopLag(now);
            this.endOutgoingHold();
            return;
        }

        this.lastClientDistance = RotationUtil.distanceToEntity(this.target);
        AxisAlignedBB serverBox = this.getServerBox(this.target, true);
        this.lastServerDistance = RotationUtil.distanceToBox(serverBox);

        if (this.isIncomingMode()) {
            this.tickIncomingMode(now);
        } else if (this.isLagTrack()) {
            this.tickLagTrack(now);
        } else if (this.isGomme()) {
            this.tickGomme(now);
        } else if (this.isClassic()) {
            this.tickClassic(now);
        }
    }

    @EventTarget(Priority.MEDIUM)
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || this.target == null || mc.theWorld == null) {
            return;
        }
        boolean active = this.windowOpen || !this.incomingQueue.isEmpty()
                || this.holdingOutgoing || this.lagActive;
        if (!active) {
            return;
        }

        if (!"None".equals(this.targetEsp.getModeString())) {
            TrackedPos position = this.serverPositions.get(this.target.getEntityId());
            if (position != null) {
                double[] renderPosition = this.renderPosition(position, event.getPartialTicks());
                if ("Classic".equals(this.targetEsp.getModeString())) {
                    this.drawTargetBox(this.target, renderPosition, new Color(this.targetColor.getValue() & 0xFFFFFF),
                            this.targetLineWidth.getValue());
                } else {
                    this.renderEntityModel(this.target, renderPosition, event.getPartialTicks(),
                            "Wireframe".equals(this.targetEsp.getModeString()), new Color(this.targetColor.getValue() & 0xFFFFFF));
                }
            }
        }

        if (!this.isOutgoingMode() || "None".equals(this.playerEsp.getModeString()) || this.lastSentPosition == null
                || mc.thePlayer == null) {
            return;
        }
        if (this.onlyInThirdPerson.getValue() && mc.gameSettings.thirdPersonView == 0) {
            return;
        }
        if (mc.thePlayer.getDistance(this.lastSentPosition.xCoord, this.lastSentPosition.yCoord, this.lastSentPosition.zCoord) < 0.1D) {
            return;
        }
        double[] renderPosition = new double[]{
                this.lastSentPosition.xCoord,
                this.lastSentPosition.yCoord,
                this.lastSentPosition.zCoord
        };
        if ("Classic".equals(this.playerEsp.getModeString())) {
            this.drawTargetBox(mc.thePlayer, renderPosition, new Color(this.playerColor.getValue() & 0xFFFFFF),
                    this.playerLineWidth.getValue());
        } else {
            this.renderEntityModel(mc.thePlayer, renderPosition, event.getPartialTicks(), false,
                    new Color(this.playerColor.getValue() & 0xFFFFFF));
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.reset(false);
    }

    /* --------------------------------------------------------- Incoming windows */

    private void tickIncomingMode(long now) {
        if (!this.isIncomingMode()) {
            return;
        }
        boolean valid = this.isValidTarget(this.target) && this.hurtTimeAllows() && this.abortAllows();
        if (this.windowOpen) {
            if (!valid || now >= this.windowDeadline) {
                this.closeWindow();
            } else if (this.isBackTrack() && this.dynamic.getValue() && this.lastServerDistance <= 3.0D) {
                this.closeWindow();
            }
            return;
        }

        if (!valid) {
            return;
        }
        if (this.isBackTrack()) {
            this.tryStartBacktrack(now, true);
        } else if (this.isPing()) {
            this.tryStartPing(now);
        }
    }

    private void tryStartBacktrack(long now, boolean fromTick) {
        if (!this.isBackTrack() || this.windowOpen) {
            return;
        }
        if (this.target == null || !this.isValidTarget(this.target)) {
            return;
        }
        if (!this.hurtTimeAllows() || !this.abortAllows()) {
            return;
        }
        if (this.clickCheck.getValue() && !Mouse.isButtonDown(0)) {
            return;
        }
        if (!this.forceDelay.getValue() && now - this.lastAttack > TARGET_TIMEOUT_MS) {
            return;
        }
        if (now - this.lastWindowEnd < this.randomBetween(this.backtrackDelayMin.getValue(), this.backtrackDelayMax.getValue())) {
            return;
        }
        if (this.lastServerDistance > this.maxHitRange.getValue()) {
            return;
        }
        if (this.lastServerDistance < this.minHitRange.getValue()) {
            return;
        }
        if (this.checkStartDistance.getValue() && this.lastServerDistance <= this.startDistance.getValue()) {
            return;
        }
        if (this.lastServerDistance <= this.lastClientDistance + this.threshold.getValue()) {
            return;
        }
        if (this.dynamic.getValue() && this.lastServerDistance <= 3.0D) {
            return;
        }
        int duration = this.randomBetween(this.backtrackTimeMin.getValue(), this.backtrackTimeMax.getValue());
        if (this.calculateWithOwnPing.getValue()) {
            duration = Math.max(0, duration - this.ping());
        }
        this.openWindow(now, duration);
    }

    private void tryStartPing(long now) {
        if (!this.isPing() || this.windowOpen || now < this.nextPingWindow) {
            return;
        }
        if (this.target == null || !this.isValidTarget(this.target) || !this.hurtTimeAllows() || !this.abortAllows()) {
            return;
        }
        if (this.lastServerDistance > this.maxHitRange.getValue()) {
            return;
        }
        if (this.dynamicPing.getValue() && this.lastServerDistance <= 3.0D) {
            return;
        }
        if (this.lastServerDistance <= this.lastClientDistance + this.threshold.getValue()) {
            return;
        }
        if (this.checkStartDistance.getValue() && this.lastServerDistance <= this.startDistance.getValue()) {
            return;
        }
        int duration = this.randomBetween(this.pingDelayMin.getValue(), this.pingDelayMax.getValue());
        if (this.calculateWithOwnPingPing.getValue()) {
            duration = Math.max(0, duration - this.ping());
        }
        this.openWindow(now, duration);
        if (duration <= 0) {
            this.closeWindow();
        }
    }

    private void tickClassic(long now) {
        if (!this.isClassic() || this.target == null) {
            return;
        }
        if (this.holdingOutgoing) {
            if (now >= this.outgoingHoldUntil) {
                this.flushOutgoing();
                this.holdingOutgoing = false;
                this.outgoingHoldUntil = 0L;
            }
            return;
        }
        if (!this.isValidTarget(this.target) || !this.hurtTimeAllows()) {
            return;
        }
        if (this.checkStartDistance.getValue() && this.lastServerDistance <= this.startDistance.getValue()) {
            return;
        }
        if (this.lastServerDistance > this.maxHitRange.getValue()
                || this.lastServerDistance <= this.lastClientDistance + this.threshold.getValue()) {
            return;
        }
        if (this.dynamicClassic.getValue() && this.lastServerDistance <= 3.0D) {
            return;
        }
        if (this.onlyWhenNeed.getValue() && this.lastClientDistance <= 3.0D
                && !(mc.thePlayer.hurtTime > 3 && mc.thePlayer.hurtTime < 8)) {
            return;
        }
        int duration = this.randomBetween(this.classicBacktrackDelayMin.getValue(), this.classicBacktrackDelayMax.getValue());
        if (duration <= 0) {
            this.flushOutgoing();
            return;
        }
        this.holdingOutgoing = true;
        this.outgoingHoldUntil = now + duration;
    }

    private void openWindow(long now, int duration) {
        this.windowDuration = duration;
        this.windowDeadline = now + duration;
        this.acceptIncoming = true;
        this.windowOpen = true;
        this.attackedSinceWindow = false;
    }

    private void closeWindow() {
        if (!this.windowOpen) {
            this.flushIncoming();
            return;
        }
        this.windowOpen = false;
        this.acceptIncoming = false;
        this.windowDeadline = 0L;
        this.attackedSinceWindow = false;
        this.lastWindowEnd = System.currentTimeMillis();
        if (this.isPing()) {
            this.nextPingWindow = this.lastWindowEnd + this.delayBetweenBacktrackPing.getValue();
        }
        this.flushIncoming();
    }

    private void endIncomingWindow() {
        this.target = null;
        this.windowOpen = false;
        this.acceptIncoming = false;
        this.windowDeadline = 0L;
        this.attackedSinceWindow = false;
        this.flushIncoming();
    }

    private void flushIncoming() {
        List<Packet<?>> packets;
        synchronized (this.holdLock) {
            if (this.incomingQueue.isEmpty()) {
                return;
            }
            packets = new ArrayList<Packet<?>>(this.incomingQueue);
            this.incomingQueue.clear();
        }
        for (Packet<?> packet : packets) {
            this.schedulePacket(packet);
        }
    }

    @SuppressWarnings("unchecked")
    private void schedulePacket(final Packet<?> packet) {
        if (mc.getNetHandler() == null) {
            return;
        }
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (mc.getNetHandler() == null) {
                    return;
                }
                Backtrack.this.replaying = true;
                try {
                    PacketEvent replayed = new PacketEvent(EventType.RECEIVE, packet);
                    EventManager.call(replayed);
                    if (!replayed.isCancelled() && mc.getNetHandler() != null) {
                        ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
                    }
                } finally {
                    Backtrack.this.replaying = false;
                }
            }
        });
    }

    /* --------------------------------------------------------- Outgoing windows */

    private void tickLagTrack(long now) {
        if (!this.isValidTarget(this.target) || !this.hurtTimeAllows() || !this.abortAllows()) {
            this.stopLag(now);
            return;
        }
        if (this.lastServerDistance > this.maxHitRange.getValue()
                || this.lastServerDistance <= this.lastClientDistance + this.threshold.getValue()) {
            this.stopLag(now);
            return;
        }
        if (this.checkStartDistance.getValue() && this.lastServerDistance <= this.startDistance.getValue()) {
            this.stopLag(now);
            return;
        }
        if (!this.lagActive) {
            if (now - this.lastLagEnd < this.delayBetweenLags.getValue()) {
                return;
            }
            this.lagActive = true;
            this.lagTicks = 0;
            this.lagMovePackets = 0;
        }

        int max = this.maxLagPackets.getValue();
        if (max <= 0) {
            this.stopLag(now);
            return;
        }
        if ("Tick".equals(this.countMode.getModeString())) {
            this.lagTicks++;
        }
        int counted = "Tick".equals(this.countMode.getModeString()) ? this.lagTicks : this.lagMovePackets;
        if (this.calculateWithOwnPingLagTrack.getValue()) {
            counted = Math.max(0, (counted * 50 - this.ping()) / 50);
        }
        if (counted > max) {
            this.stopLag(now);
            return;
        }
        if (CrewX.lagManager != null) {
            CrewX.lagManager.setDelay(max - counted + 1);
        }
    }

    private void stopLag(long now) {
        if (!this.lagActive && (CrewX.lagManager == null || CrewX.lagManager.getTickDelay() == 0)) {
            return;
        }
        this.lagActive = false;
        this.lagTicks = 0;
        this.lagMovePackets = 0;
        this.attackedSinceWindow = false;
        this.lastLagEnd = now;
        if (CrewX.lagManager != null && this.isLagTrack()) {
            CrewX.lagManager.setDelay(0);
        }
    }

    private void tickGomme(long now) {
        if (!this.isValidTarget(this.target) || !this.hurtTimeAllows() || !this.abortAllows()) {
            this.endOutgoingHold();
            return;
        }
        if (this.lastServerDistance > this.maxHitRange.getValue()
                || this.lastServerDistance <= this.lastClientDistance + this.threshold.getValue()) {
            this.endOutgoingHold();
            return;
        }
        if (this.holdingOutgoing) {
            if (now >= this.outgoingHoldUntil) {
                this.flushOutgoing();
                this.holdingOutgoing = false;
                this.nextOutgoingHold = now + 50L;
            }
            return;
        }
        if (now >= this.nextOutgoingHold) {
            this.startOutgoingHold(now);
        }
    }

    private void startOutgoingHold(long now) {
        if (!this.isGomme() || this.holdingOutgoing || this.target == null) {
            return;
        }
        int ticks = this.lagPackets.getValue();
        if (ticks <= 0) {
            return;
        }
        this.holdingOutgoing = true;
        this.outgoingHoldUntil = now + ticks * 50L;
    }

    private void endOutgoingHold() {
        this.holdingOutgoing = false;
        this.outgoingHoldUntil = 0L;
        this.attackedSinceWindow = false;
        this.flushOutgoing();
    }

    private void flushOutgoing() {
        List<Packet<?>> packets;
        synchronized (this.holdLock) {
            if (this.outgoingQueue.isEmpty()) {
                return;
            }
            packets = new ArrayList<Packet<?>>(this.outgoingQueue);
            this.outgoingQueue.clear();
        }
        this.flushingOutgoing = true;
        try {
            for (Packet<?> packet : packets) {
                PacketUtil.sendPacketNoEvent(packet);
            }
        } finally {
            this.flushingOutgoing = false;
        }
    }

    /* ------------------------------------------------------------------- Target */

    private void updateTarget(long now) {
        KillAura killAura = (KillAura) CrewX.moduleManager.modules.get(KillAura.class);
        EntityLivingBase candidate = null;
        if (killAura != null && killAura.isEnabled() && killAura.target != null) {
            candidate = killAura.target.getEntity();
        }
        if (candidate != null && !this.isValidTarget(candidate)) {
            candidate = null;
        }
        if (candidate == null && !this.onlyKillAura.getValue()) {
            candidate = this.findClosestTarget();
        }
        if (candidate == this.target) {
            if (this.target != null && !this.isValidTarget(this.target)) {
                this.closeWindow();
                this.target = null;
            }
            return;
        }
        this.closeWindow();
        this.endOutgoingHold();
        this.target = candidate;
        this.lastAttack = 0L;
        if (candidate != null) {
            this.seedPosition(candidate, now);
        }
    }

    private EntityLivingBase findClosestTarget() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        EntityLivingBase closest = null;
        double bestDistance = this.preAimRange.getValue();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!this.matchesEntityType(living) || !this.isValidTarget(living)) {
                continue;
            }
            double distance = RotationUtil.distanceToEntity(living);
            if (distance <= bestDistance) {
                bestDistance = distance;
                closest = living;
            }
        }
        return closest;
    }

    private boolean matchesEntityType(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            return this.targetPlayers.getValue();
        }
        if (entity instanceof EntityMob) {
            return this.targetMobs.getValue();
        }
        if (entity instanceof EntityAnimal) {
            return this.targetAnimals.getValue();
        }
        if (entity instanceof EntityVillager) {
            return this.targetVillagers.getValue();
        }
        if (entity instanceof EntityArmorStand) {
            return this.targetArmorStands.getValue();
        }
        return false;
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == null || entity == mc.thePlayer || entity.isDead || entity.getHealth() <= 0.0F
                || !TeamUtil.isEntityLoaded(entity)) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (TeamUtil.isFriend(player)) {
                return false;
            }
            if (this.teams.getValue() && TeamUtil.isSameTeam(player)) {
                return false;
            }
            AntiBot antiBot = (AntiBot) CrewX.moduleManager.modules.get(AntiBot.class);
            if (antiBot != null && antiBot.isEnabled() && antiBot.isBot(player)) {
                return false;
            }
        }
        if (this.notWhileScaffold.getValue()) {
            Scaffold scaffold = (Scaffold) CrewX.moduleManager.modules.get(Scaffold.class);
            if (scaffold != null && scaffold.isEnabled()) {
                return false;
            }
        }
        return true;
    }

    private boolean hurtTimeAllows() {
        if (!this.stopBacktrackAtHurtTime.getValue() || mc.thePlayer == null) {
            return true;
        }
        return !this.hurtTimeBlocked || mc.thePlayer.hurtTime <= this.continueWithBacktrackAtHurtTime.getValue();
    }

    private void updateHurtTimeState() {
        if (mc.thePlayer == null || !this.stopBacktrackAtHurtTime.getValue()) {
            this.hurtTimeBlocked = false;
            return;
        }
        if (mc.thePlayer.hurtTime == 10) {
            this.hurtTimeBlocked = true;
        } else if (mc.thePlayer.hurtTime == 0) {
            this.hurtTimeBlocked = false;
        }
    }

    private boolean abortAllows() {
        String abort = this.abortCriterion.getModeString();
        if ("None".equals(abort)) {
            return true;
        }
        int hurtTime = this.stopOnAttackMaxHurtTime.getValue();
        if ("OnAttack".equals(abort)) {
            return !this.attackedSinceWindow;
        }
        if ("OnRange".equals(abort)) {
            return this.lastServerDistance > this.stopOnAttackRange.getValue() || this.target == null || this.target.hurtTime > hurtTime;
        }
        return true;
    }

    /* ---------------------------------------------------------------- Positions */

    private void updateServerPosition(Packet<?> packet) {
        EntityLivingBase currentTarget = this.target;
        if (currentTarget == null || mc.theWorld == null) {
            return;
        }
        int targetId = currentTarget.getEntityId();
        long now = System.currentTimeMillis();
        if (packet instanceof S14PacketEntity) {
            S14PacketEntity movement = (S14PacketEntity) packet;
            Entity entity = movement.getEntity(mc.theWorld);
            if (entity == null || entity.getEntityId() != targetId) {
                return;
            }
            TrackedPos previous = this.serverPositions.get(targetId);
            double baseX = previous != null ? previous.x : currentTarget.posX;
            double baseY = previous != null ? previous.y : currentTarget.posY;
            double baseZ = previous != null ? previous.z : currentTarget.posZ;
            this.serverPositions.put(targetId, new TrackedPos(
                    baseX + movement.func_149062_c() / 32.0D,
                    baseY + movement.func_149061_d() / 32.0D,
                    baseZ + movement.func_149064_e() / 32.0D,
                    baseX, baseY, baseZ, now));
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
            if (teleport.getEntityId() != targetId) {
                return;
            }
            TrackedPos previous = this.serverPositions.get(targetId);
            double baseX = previous != null ? previous.x : currentTarget.posX;
            double baseY = previous != null ? previous.y : currentTarget.posY;
            double baseZ = previous != null ? previous.z : currentTarget.posZ;
            this.serverPositions.put(targetId, new TrackedPos(
                    teleport.getX() / 32.0D, teleport.getY() / 32.0D, teleport.getZ() / 32.0D,
                    baseX, baseY, baseZ, now));
        }
    }

    private void seedPosition(EntityLivingBase entity, long now) {
        this.serverPositions.put(entity.getEntityId(), new TrackedPos(
                entity.posX, entity.posY, entity.posZ, entity.posX, entity.posY, entity.posZ, now));
    }

    private AxisAlignedBB getServerBox(EntityLivingBase entity, boolean withPrediction) {
        TrackedPos position = this.serverPositions.get(entity.getEntityId());
        AxisAlignedBB box = entity.getEntityBoundingBox();
        if (position == null) {
            return box;
        }
        double offsetX = position.x - entity.posX;
        double offsetY = position.y - entity.posY;
        double offsetZ = position.z - entity.posZ;
        if (withPrediction && this.predict.getValue() && this.isBackTrack()
                && System.currentTimeMillis() - position.updated <= POSITION_STALE_MS) {
            offsetX += clamp(position.x - position.prevX, 0.5D);
            offsetY += clamp(position.y - position.prevY, 0.5D);
            offsetZ += clamp(position.z - position.prevZ, 0.5D);
        }
        return box.offset(offsetX, offsetY, offsetZ);
    }

    private double[] renderPosition(TrackedPos position, float partialTicks) {
        if (!this.smoothRendering.getValue() || System.currentTimeMillis() - position.updated > POSITION_STALE_MS) {
            return new double[]{position.x, position.y, position.z};
        }
        return new double[]{
                RenderUtil.lerpDouble(position.x, position.prevX, partialTicks),
                RenderUtil.lerpDouble(position.y, position.prevY, partialTicks),
                RenderUtil.lerpDouble(position.z, position.prevZ, partialTicks)
        };
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private void drawTargetBox(EntityLivingBase entity, double[] position, Color color, float lineWidth) {
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        AxisAlignedBB box = entity.getEntityBoundingBox()
                .offset(position[0] - entity.posX, position[1] - entity.posY, position[2] - entity.posZ)
                .offset(-renderManager.getRenderPosX(), -renderManager.getRenderPosY(), -renderManager.getRenderPosZ());
        RenderUtil.enableRenderState();
        RenderUtil.drawStyledBox(box, color.getRGB() & 0xFFFFFF, 0.24F, 0.04F, lineWidth);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderUtil.disableRenderState();
    }

    private void renderEntityModel(EntityLivingBase entity, double[] position, float partialTicks,
                                   boolean wireframe, Color color) {
        double oldX = entity.posX;
        double oldY = entity.posY;
        double oldZ = entity.posZ;
        double oldLastX = entity.lastTickPosX;
        double oldLastY = entity.lastTickPosY;
        double oldLastZ = entity.lastTickPosZ;
        double[] glCache = GlStateSync.snapshot();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            if (wireframe) {
                RenderUtil.enableRenderState();
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glLineWidth(this.targetLineWidth.getValue());
                GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 1.0F);
            } else {
                GlStateManager.enableTexture2D();
                GlStateManager.enableDepth();
                GlStateManager.enableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            }
            entity.setPosition(position[0], position[1], position[2]);
            entity.lastTickPosX = position[0];
            entity.lastTickPosY = position[1];
            entity.lastTickPosZ = position[2];
            mc.getRenderManager().renderEntitySimple(entity, partialTicks);
        } finally {
            entity.setPosition(oldX, oldY, oldZ);
            entity.lastTickPosX = oldLastX;
            entity.lastTickPosY = oldLastY;
            entity.lastTickPosZ = oldLastZ;
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GL11.glLineWidth(2.0F);
            GL11.glPopAttrib();
            GlStateSync.afterPopAttrib(glCache);
        }
    }

    /* -------------------------------------------------------------------- Misc */

    private boolean isTargetDead(S1CPacketEntityMetadata packet, EntityLivingBase entity) {
        if (packet.getEntityId() != entity.getEntityId()) {
            return false;
        }
        List<?> values = packet.func_149376_c();
        if (values == null) {
            return false;
        }
        for (Object object : values) {
            if (!(object instanceof net.minecraft.entity.DataWatcher.WatchableObject)) {
                continue;
            }
            net.minecraft.entity.DataWatcher.WatchableObject value = (net.minecraft.entity.DataWatcher.WatchableObject) object;
            if (value.getDataValueId() == 6 && value.getObject() instanceof Number
                    && ((Number) value.getObject()).doubleValue() <= 0.0D) {
                return true;
            }
        }
        return false;
    }

    private boolean isIgnoredPacket(Packet<?> packet) {
        return packet instanceof C00Handshake
                || packet instanceof S00PacketKeepAlive
                || packet instanceof S01PacketJoinGame
                || packet instanceof S02PacketChat
                || packet instanceof S3FPacketCustomPayload
                || packet instanceof S40PacketDisconnect
                || packet instanceof S48PacketResourcePackSend
                || packet instanceof S29PacketSoundEffect && this.isNonDelayedSound((S29PacketSoundEffect) packet);
    }

    private boolean isNonDelayedSound(S29PacketSoundEffect packet) {
        String name = packet.getSoundName();
        return name != null && (name.contains("game.player.hurt") || name.contains("game.player.die"));
    }

    private boolean isIgnoredOutgoing(Packet<?> packet) {
        return packet instanceof C00PacketKeepAlive
                || packet instanceof C01PacketChatMessage
                || packet instanceof C00Handshake;
    }

    private int ping() {
        try {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            return playerInfo == null ? 0 : Math.max(0, playerInfo.getResponseTime());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int randomBetween(int first, int second) {
        int min = Math.min(first, second);
        int max = Math.max(first, second);
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    @Override
    public String[] getSuffix() {
        if (this.windowOpen) {
            long remaining = Math.max(0L, this.windowDeadline - System.currentTimeMillis());
            return new String[]{remaining + "ms"};
        }
        if (this.isLagTrack()) {
            return new String[]{this.maxLagPackets.getValue() + "t"};
        }
        if (this.isGomme()) {
            return new String[]{this.lagPackets.getValue() + "t"};
        }
        return new String[]{this.mode.getModeString()};
    }

    private static final class TrackedPos {
        private final double x;
        private final double y;
        private final double z;
        private final double prevX;
        private final double prevY;
        private final double prevZ;
        private final long updated;

        private TrackedPos(double x, double y, double z, double prevX, double prevY, double prevZ, long updated) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.prevX = prevX;
            this.prevY = prevY;
            this.prevZ = prevZ;
            this.updated = updated;
        }
    }
}
