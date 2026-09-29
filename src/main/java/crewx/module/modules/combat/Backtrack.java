package crewx.module.modules.combat;

/*
 * Adapted from LibreBounce Backtrack.
 * Upstream: https://github.com/LibreBounce/LibreBounce/blob/main/src/main/java/net/ccbluex/liquidbounce/features/module/modules/combat/Backtrack.kt
 * Copyright (c) CCBlueX contributors. Licensed under GPL-3.0.
 */

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.AttackEvent;
import crewx.events.LoadWorldEvent;
import crewx.events.PacketEvent;
import crewx.events.Render3DEvent;
import crewx.events.TickEvent;
import crewx.module.Module;
import crewx.module.modules.misc.AntiBot;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ColorProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.IntProperty;
import crewx.property.properties.ModeProperty;
import crewx.mixin.IAccessorRenderManager;
import crewx.util.RenderUtil;
import crewx.util.RotationUtil;
import crewx.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.server.S01PacketPong;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldSettings;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class Backtrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final IntProperty nextBacktrackDelay = new IntProperty("NextBacktrackDelay", 0, 0, 10000);
    private final IntProperty delayMin = new IntProperty("DelayMin", 80, 0, 10000);
    private final IntProperty delayMax = new IntProperty("DelayMax", 80, 0, 10000);
    private final ModeProperty style = new ModeProperty("Style", 1, new String[]{"Pulse", "Smooth"});
    private final FloatProperty distanceMin = new FloatProperty("DistanceMin", 2.0F, 0.0F, 6.0F);
    private final FloatProperty distanceMax = new FloatProperty("DistanceMax", 3.0F, 0.0F, 6.0F);
    private final BooleanProperty smart = new BooleanProperty("Smart", true);
    private final FloatProperty advantageThreshold = new FloatProperty("AdvantageThreshold", 0.0F, 0.0F, 1.0F,
            () -> this.smart.getValue());
    private final ModeProperty targetHurtTimeHandling = new ModeProperty("TargetHurtTimeHandling", 2,
            new String[]{"Allow", "Forbid", "Ignore"});
    private final IntProperty targetHurtTimeMin = new IntProperty("TargetHurtTimeMin", 0, 0, 10,
            () -> !"Ignore".equals(this.targetHurtTimeHandling.getModeString()));
    private final IntProperty targetHurtTimeMax = new IntProperty("TargetHurtTimeMax", 1, 0, 10,
            () -> !"Ignore".equals(this.targetHurtTimeHandling.getModeString()));
    private final ModeProperty ownHurtTimeHandling = new ModeProperty("OwnHurtTimeHandling", 2,
            new String[]{"Allow", "Forbid", "Ignore"});
    private final IntProperty ownHurtTimeMin = new IntProperty("OwnHurtTimeMin", 9, 0, 10,
            () -> !"Ignore".equals(this.ownHurtTimeHandling.getModeString()));
    private final IntProperty ownHurtTimeMax = new IntProperty("OwnHurtTimeMax", 10, 0, 10,
            () -> !"Ignore".equals(this.ownHurtTimeHandling.getModeString()));
    private final ModeProperty espMode = new ModeProperty("ESPMode", 1,
            new String[]{"None", "Box", "Model", "Wireframe"});
    private final FloatProperty wireframeWidth = new FloatProperty("WireframeWidth", 1.0F, 0.5F, 5.0F,
            () -> "Wireframe".equals(this.espMode.getModeString()));
    private final ColorProperty espColor = new ColorProperty("ESPColor", 0x00FF00,
            () -> !"None".equals(this.espMode.getModeString()));
    private final BooleanProperty debug = new BooleanProperty("Debug", false);
    private final IntProperty targetHurtTimeToDebugMin = new IntProperty("TargetHurtTimeToDebugMin", 0, 0, 10,
            () -> this.debug.getValue());
    private final IntProperty targetHurtTimeToDebugMax = new IntProperty("TargetHurtTimeToDebugMax", 1, 0, 10,
            () -> this.debug.getValue());
    private final IntProperty targetFlushDelay = new IntProperty("TargetFlushDelay", 1000, 100, 10000);

    private final ConcurrentLinkedQueue<QueuedPacket> packetQueue = new ConcurrentLinkedQueue<QueuedPacket>();
    private final ConcurrentLinkedQueue<PositionSample> positions = new ConcurrentLinkedQueue<PositionSample>();
    private final Map<Integer, ServerPosition> serverPositions = new ConcurrentHashMap<Integer, ServerPosition>();

    private volatile EntityLivingBase target;
    private volatile long lastAttack;
    private volatile long delayForNextBacktrack;
    private volatile long pulseStartedAt;
    private volatile long currentDelay = 80L;
    private volatile boolean shouldRender;
    private volatile boolean wasBacktracking;
    private volatile ServerPosition renderPosition;
    private volatile double previousRenderX;
    private volatile double previousRenderY;
    private volatile double previousRenderZ;
    private volatile double currentRenderX;
    private volatile double currentRenderY;
    private volatile double currentRenderZ;
    private volatile double targetRenderX;
    private volatile double targetRenderY;
    private volatile double targetRenderZ;
    private volatile int interpolationSteps;

    public Backtrack() {
        super("Backtrack", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.currentDelay + "ms"};
    }

    @EventTarget(Priority.HIGHEST)
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || event.isCancelled() || !(event.getTarget() instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase nextTarget = (EntityLivingBase) event.getTarget();
        if (!this.isValidTarget(nextTarget)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.target != nextTarget) {
            this.clearPackets(true);
            this.resetTargetState();
            this.target = nextTarget;
            ServerPosition known = this.serverPositions.get(nextTarget.getEntityId());
            if (known == null) {
                known = new ServerPosition(nextTarget.posX, nextTarget.posY, nextTarget.posZ);
                this.serverPositions.put(nextTarget.getEntityId(), known);
            }
            this.setTargetRenderPosition(known.x, known.y, known.z);
        }
        this.lastAttack = now;
        this.pulseStartedAt = now;
        this.currentDelay = this.randomDelay();
        this.shouldRender = true;
    }

    @EventTarget(Priority.LOW)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null
                || mc.isSingleplayer() || mc.getCurrentServerData() == null) {
            this.clearPackets(mc.getNetHandler() != null);
            this.resetTargetState();
            return;
        }

        Packet<?> packet = event.getPacket();
        if (this.isIgnoredPacket(packet)) {
            return;
        }
        if (packet instanceof S06PacketUpdateHealth && ((S06PacketUpdateHealth) packet).getHealth() <= 0.0F) {
            this.clearPackets(true);
            this.resetTargetState();
            return;
        }
        if (packet instanceof S13PacketDestroyEntities) {
            int targetId = this.target == null ? -1 : this.target.getEntityId();
            for (int entityId : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                this.serverPositions.remove(entityId);
                if (entityId == targetId) {
                    this.clearPackets(true);
                    this.resetTargetState();
                    return;
                }
            }
        }
        if (packet instanceof S1CPacketEntityMetadata && this.isTargetDead((S1CPacketEntityMetadata) packet)) {
            this.clearPackets(true);
            this.resetTargetState();
            return;
        }
        if (packet instanceof S19PacketEntityStatus && this.target != null
                && ((S19PacketEntityStatus) packet).getEntity(mc.theWorld) == this.target) {
            return;
        }

        long now = System.currentTimeMillis();
        this.processTrackedPacket(packet, now);
        this.expireTargetIfNeeded(now);
        if (!this.shouldBacktrack(now)) {
            if (!this.packetQueue.isEmpty()) {
                this.clearPackets(true);
            }
            return;
        }
        event.setCancelled(true);
        this.packetQueue.offer(new QueuedPacket(packet, now));
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        long now = System.currentTimeMillis();
        this.expireTargetIfNeeded(now);
        boolean canBacktrack = this.shouldBacktrack(now);
        if (!canBacktrack) {
            if (!this.packetQueue.isEmpty()) {
                this.clearPackets(true);
            }
            if (this.wasBacktracking) {
                this.delayForNextBacktrack = now + this.nextBacktrackDelay.getValue();
            }
            this.wasBacktracking = false;
            this.shouldRender = false;
            return;
        }

        this.wasBacktracking = true;
        EntityLivingBase currentTarget = this.target;
        ServerPosition serverPosition = currentTarget == null ? null : this.serverPositions.get(currentTarget.getEntityId());
        if (serverPosition == null && currentTarget != null) {
            serverPosition = new ServerPosition(currentTarget.posX, currentTarget.posY, currentTarget.posZ);
        }
        if (currentTarget == null || serverPosition == null || !this.hasBacktrackAdvantage(currentTarget, serverPosition)) {
            this.clearPackets(true);
            this.shouldRender = false;
            return;
        }

        this.shouldRender = true;
        if ("Pulse".equals(this.style.getModeString()) && now - this.pulseStartedAt >= this.currentDelay) {
            this.clearPackets(true);
            this.pulseStartedAt = now;
            this.currentDelay = this.randomDelay();
            this.positions.clear();
        }

        double currentDistance = RotationUtil.distanceToEntity(currentTarget);
        if (this.inConfiguredDistance(currentDistance)) {
            this.releaseBefore(now - this.currentDelay);
        } else {
            long rangeTime = this.findRangeTimestamp(currentTarget);
            if (rangeTime < 0L) {
                this.clearPackets(true);
            } else {
                this.releaseBefore(rangeTime);
            }
        }
        this.prunePositions(now - 10000L);
        this.interpolateRenderPosition();
        if (this.debug.getValue() && currentTarget.hurtTime >= this.targetHurtTimeToDebugMin.getValue()
                && currentTarget.hurtTime <= this.targetHurtTimeToDebugMax.getValue()) {
            double renderedDistance = mc.thePlayer.getDistanceToEntity(currentTarget);
            double realDistance = mc.thePlayer.getDistance(serverPosition.x, serverPosition.y, serverPosition.z);
            if (mc.thePlayer.ticksExisted % 20 == 0) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        "(Backtrack) Lag distance: " + renderedDistance + ", true distance: " + realDistance));
            }
        }
    }

    @EventTarget(Priority.MEDIUM)
    public void onRender3D(Render3DEvent event) {
        EntityLivingBase currentTarget = this.target;
        if (!this.isEnabled() || !this.shouldRender || currentTarget == null || mc.theWorld == null
                || "None".equals(this.espMode.getModeString())) {
            return;
        }
        ServerPosition position = this.renderPosition;
        if (position == null) {
            position = this.serverPositions.get(currentTarget.getEntityId());
        }
        if (position == null) {
            return;
        }
        double x = RenderUtil.lerpDouble(this.currentRenderX, this.previousRenderX, event.getPartialTicks());
        double y = RenderUtil.lerpDouble(this.currentRenderY, this.previousRenderY, event.getPartialTicks());
        double z = RenderUtil.lerpDouble(this.currentRenderZ, this.previousRenderZ, event.getPartialTicks());
        if (this.interpolationSteps <= 0) {
            x = position.x;
            y = position.y;
            z = position.z;
        }

        String mode = this.espMode.getModeString();
        if ("Model".equals(mode) || "Wireframe".equals(mode)) {
            this.renderTargetModel(currentTarget, x, y, z, event.getPartialTicks(), "Wireframe".equals(mode));
            return;
        }

        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        double offsetX = x - currentTarget.posX;
        double offsetY = y - currentTarget.posY;
        double offsetZ = z - currentTarget.posZ;
        AxisAlignedBB box = currentTarget.getEntityBoundingBox().offset(offsetX, offsetY, offsetZ)
                .offset(-renderManager.getRenderPosX(), -renderManager.getRenderPosY(), -renderManager.getRenderPosZ());
        Color color = new Color(this.espColor.getValue() & 0xFFFFFF);
        RenderUtil.enableRenderState();
        GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 1.0F);
        RenderUtil.drawFilledBox(box, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.drawBoundingBox(box, color.getRed(), color.getGreen(), color.getBlue(), 220, 1.6F);
        RenderUtil.disableRenderState();
    }

    @EventTarget(Priority.MEDIUM)
    public void onLoadWorld(LoadWorldEvent event) {
        this.clearPackets(mc.theWorld != null && mc.getNetHandler() != null);
        this.resetTargetState();
        this.serverPositions.clear();
    }

    @Override
    public void onEnabled() {
        this.clearPackets(false);
        this.resetTargetState();
        this.serverPositions.clear();
    }

    @Override
    public void onDisabled() {
        this.clearPackets(true);
        this.resetTargetState();
        this.serverPositions.clear();
    }

    private boolean isIgnoredPacket(Packet<?> packet) {
        return packet instanceof C00Handshake || packet instanceof C00PacketServerQuery
                || packet instanceof S02PacketChat || packet instanceof S01PacketPong
                || packet instanceof S29PacketSoundEffect && this.isNonDelayedSound((S29PacketSoundEffect) packet);
    }

    private boolean isNonDelayedSound(S29PacketSoundEffect packet) {
        String name = packet.getSoundName();
        return name != null && (name.contains("game.player.hurt") || name.contains("game.player.die"));
    }

    private boolean isTargetDead(S1CPacketEntityMetadata packet) {
        EntityLivingBase currentTarget = this.target;
        if (currentTarget == null || packet.getEntityId() != currentTarget.getEntityId()) {
            return false;
        }
        List<DataWatcher.WatchableObject> values = packet.func_149376_c();
        if (values == null) {
            return false;
        }
        for (DataWatcher.WatchableObject value : values) {
            if (value.getDataValueId() == 6 && value.getObject() instanceof Number
                    && ((Number) value.getObject()).doubleValue() <= 0.0D) {
                return true;
            }
        }
        return false;
    }

    private void processTrackedPacket(Packet<?> packet, long timestamp) {
        EntityLivingBase currentTarget = this.target;
        int targetId = currentTarget == null ? -1 : currentTarget.getEntityId();
        int entityId = -1;
        ServerPosition position = null;
        if (packet instanceof S14PacketEntity && mc.theWorld != null) {
            S14PacketEntity movement = (S14PacketEntity) packet;
            Entity entity = movement.getEntity(mc.theWorld);
            if (entity != null) {
                entityId = entity.getEntityId();
                ServerPosition previous = this.serverPositions.get(entityId);
                if (previous == null) {
                    previous = new ServerPosition(entity.posX, entity.posY, entity.posZ);
                }
                position = new ServerPosition(previous.x + movement.func_149062_c() / 32.0D,
                        previous.y + movement.func_149061_d() / 32.0D,
                        previous.z + movement.func_149064_e() / 32.0D);
                this.serverPositions.put(entityId, position);
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
            entityId = teleport.getEntityId();
            position = new ServerPosition(teleport.getX() / 32.0D,
                    teleport.getY() / 32.0D, teleport.getZ() / 32.0D);
            this.serverPositions.put(entityId, position);
        }
        if (entityId == targetId && position != null) {
            this.positions.offer(new PositionSample(position, timestamp));
            this.setTargetRenderPosition(position.x, position.y, position.z);
        }
    }

    private boolean shouldBacktrack(long now) {
        EntityLivingBase currentTarget = this.target;
        if (mc.thePlayer == null || mc.theWorld == null || currentTarget == null || mc.thePlayer.getHealth() <= 0.0F
                || (currentTarget.getHealth() <= 0.0F && !Float.isNaN(currentTarget.getHealth()))
                || mc.playerController == null || mc.playerController.getCurrentGameType() == WorldSettings.GameType.SPECTATOR
                || now < this.delayForNextBacktrack || mc.thePlayer.ticksExisted <= 20
                || this.lastAttack == 0L || now - this.lastAttack >= this.targetFlushDelay.getValue()
                || !this.isValidTarget(currentTarget) || !this.onAllowedHurtTime(currentTarget)) {
            return false;
        }
        if ("Pulse".equals(this.style.getModeString()) && now - this.pulseStartedAt >= this.currentDelay) {
            return true;
        }
        return true;
    }

    private boolean onAllowedHurtTime(EntityLivingBase currentTarget) {
        boolean playerAllowed = this.handleHurtTime(this.ownHurtTimeHandling.getModeString(), mc.thePlayer.hurtTime,
                this.ownHurtTimeMin.getValue(), this.ownHurtTimeMax.getValue());
        boolean targetAllowed = this.handleHurtTime(this.targetHurtTimeHandling.getModeString(), currentTarget.hurtTime,
                this.targetHurtTimeMin.getValue(), this.targetHurtTimeMax.getValue());
        return playerAllowed && targetAllowed;
    }

    private boolean handleHurtTime(String mode, int hurtTime, int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        if ("Allow".equals(mode)) {
            return hurtTime >= low && hurtTime <= high;
        }
        if ("Forbid".equals(mode)) {
            return hurtTime < low || hurtTime > high;
        }
        return true;
    }

    private boolean hasBacktrackAdvantage(EntityLivingBase currentTarget, ServerPosition serverPosition) {
        double trueDistance = mc.thePlayer.getDistance(serverPosition.x, serverPosition.y, serverPosition.z);
        if (trueDistance > 6.0D) {
            return false;
        }
        double currentDistance = mc.thePlayer.getDistance(currentTarget.posX, currentTarget.posY, currentTarget.posZ);
        return !this.smart.getValue() || trueDistance > currentDistance + this.advantageThreshold.getValue();
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == null || entity == mc.thePlayer || entity.isDead || !TeamUtil.isEntityLoaded(entity)) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            try {
                if (TeamUtil.isSameTeam(player) || TeamUtil.isFriend(player)) {
                    return false;
                }
            } catch (Throwable ignored) {
            }
            AntiBot antiBot = CrewX.moduleManager == null ? null
                    : (AntiBot) CrewX.moduleManager.modules.get(AntiBot.class);
            return antiBot == null || !antiBot.isEnabled() || !antiBot.isBot(player);
        }
        return true;
    }

    private void expireTargetIfNeeded(long now) {
        long attackTime = this.lastAttack;
        if (attackTime == 0L || now - attackTime < this.targetFlushDelay.getValue() || this.target == null) {
            return;
        }
        this.clearPackets(true);
        this.resetTargetState();
        this.delayForNextBacktrack = now + this.nextBacktrackDelay.getValue();
    }

    private void releaseBefore(long timestamp) {
        while (true) {
            QueuedPacket queued = this.packetQueue.peek();
            if (queued == null || queued.timestamp > timestamp) {
                break;
            }
            this.packetQueue.poll();
            this.schedulePacket(queued.packet);
        }
        while (true) {
            PositionSample sample = this.positions.peek();
            if (sample == null || sample.timestamp > timestamp) {
                break;
            }
            this.positions.poll();
        }
    }

    private long findRangeTimestamp(EntityLivingBase currentTarget) {
        float min = Math.min(this.distanceMin.getValue(), this.distanceMax.getValue());
        float max = Math.max(this.distanceMin.getValue(), this.distanceMax.getValue());
        for (PositionSample sample : this.positions) {
            AxisAlignedBB historicalBox = currentTarget.getEntityBoundingBox().offset(
                    sample.position.x - currentTarget.posX,
                    sample.position.y - currentTarget.posY,
                    sample.position.z - currentTarget.posZ);
            double distance = RotationUtil.distanceToBox(historicalBox);
            if (distance >= min && distance <= max) {
                return sample.timestamp;
            }
        }
        return -1L;
    }

    private boolean inConfiguredDistance(double distance) {
        float min = Math.min(this.distanceMin.getValue(), this.distanceMax.getValue());
        float max = Math.max(this.distanceMin.getValue(), this.distanceMax.getValue());
        return distance >= min && distance <= max;
    }

    private void prunePositions(long cutoff) {
        while (true) {
            PositionSample sample = this.positions.peek();
            if (sample == null || sample.timestamp >= cutoff) {
                break;
            }
            this.positions.poll();
        }
        while (this.positions.size() > 256) {
            this.positions.poll();
        }
    }

    private void clearPackets(boolean process) {
        QueuedPacket queued;
        while ((queued = this.packetQueue.poll()) != null) {
            if (process) {
                this.schedulePacket(queued.packet);
            }
        }
        this.positions.clear();
        this.shouldRender = false;
    }

    @SuppressWarnings("unchecked")
    private void schedulePacket(final Packet<?> packet) {
        if (mc.getNetHandler() == null) {
            return;
        }
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (mc.getNetHandler() != null) {
                    ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
                }
            }
        });
    }

    private void resetTargetState() {
        this.target = null;
        this.lastAttack = 0L;
        this.pulseStartedAt = 0L;
        this.shouldRender = false;
        this.wasBacktracking = false;
        this.renderPosition = null;
        this.interpolationSteps = 0;
        this.delayForNextBacktrack = 0L;
        this.currentDelay = this.randomDelay();
        this.previousRenderX = 0.0D;
        this.previousRenderY = 0.0D;
        this.previousRenderZ = 0.0D;
        this.currentRenderX = 0.0D;
        this.currentRenderY = 0.0D;
        this.currentRenderZ = 0.0D;
        this.targetRenderX = 0.0D;
        this.targetRenderY = 0.0D;
        this.targetRenderZ = 0.0D;
    }

    private long randomDelay() {
        int min = Math.min(this.delayMin.getValue(), this.delayMax.getValue());
        int max = Math.max(this.delayMin.getValue(), this.delayMax.getValue());
        return ThreadLocalRandom.current().nextLong((long) min, (long) max + 1L);
    }

    private void setTargetRenderPosition(double x, double y, double z) {
        if (this.renderPosition == null) {
            this.previousRenderX = x;
            this.previousRenderY = y;
            this.previousRenderZ = z;
            this.currentRenderX = x;
            this.currentRenderY = y;
            this.currentRenderZ = z;
        } else {
            this.previousRenderX = this.currentRenderX;
            this.previousRenderY = this.currentRenderY;
            this.previousRenderZ = this.currentRenderZ;
        }
        this.targetRenderX = x;
        this.targetRenderY = y;
        this.targetRenderZ = z;
        this.renderPosition = new ServerPosition(x, y, z);
        this.interpolationSteps = 2;
    }

    private void interpolateRenderPosition() {
        if (this.interpolationSteps <= 0) {
            return;
        }
        this.previousRenderX = this.currentRenderX;
        this.previousRenderY = this.currentRenderY;
        this.previousRenderZ = this.currentRenderZ;
        this.currentRenderX += (this.targetRenderX - this.currentRenderX) / this.interpolationSteps;
        this.currentRenderY += (this.targetRenderY - this.currentRenderY) / this.interpolationSteps;
        this.currentRenderZ += (this.targetRenderZ - this.currentRenderZ) / this.interpolationSteps;
        this.interpolationSteps--;
    }

    private void renderTargetModel(EntityLivingBase entity, double x, double y, double z, float partialTicks,
                                   boolean wireframe) {
        double oldX = entity.posX;
        double oldY = entity.posY;
        double oldZ = entity.posZ;
        double oldLastX = entity.lastTickPosX;
        double oldLastY = entity.lastTickPosY;
        double oldLastZ = entity.lastTickPosZ;
        RenderUtil.enableRenderState();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            if (wireframe) {
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glLineWidth(this.wireframeWidth.getValue());
                Color color = new Color(this.espColor.getValue() & 0xFFFFFF);
                GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F,
                        color.getBlue() / 255.0F, 1.0F);
            } else {
                GL11.glColor4f(0.6F, 0.6F, 0.6F, 1.0F);
            }
            entity.setPosition(x, y, z);
            entity.lastTickPosX = x;
            entity.lastTickPosY = y;
            entity.lastTickPosZ = z;
            mc.getRenderManager().renderEntitySimple(entity, partialTicks);
        } finally {
            entity.setPosition(oldX, oldY, oldZ);
            entity.lastTickPosX = oldLastX;
            entity.lastTickPosY = oldLastY;
            entity.lastTickPosZ = oldLastZ;
            GL11.glPopAttrib();
            RenderUtil.disableRenderState();
        }
    }

    private static final class QueuedPacket {
        private final Packet<?> packet;
        private final long timestamp;

        private QueuedPacket(Packet<?> packet, long timestamp) {
            this.packet = packet;
            this.timestamp = timestamp;
        }
    }

    private static final class PositionSample {
        private final ServerPosition position;
        private final long timestamp;

        private PositionSample(ServerPosition position, long timestamp) {
            this.position = position;
            this.timestamp = timestamp;
        }
    }

    private static final class ServerPosition {
        private final double x;
        private final double y;
        private final double z;

        private ServerPosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
