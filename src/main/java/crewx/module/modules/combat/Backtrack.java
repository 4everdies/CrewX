package crewx.module.modules.combat;

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
import crewx.module.modules.render.GuiModule;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.FloatProperty;
import crewx.property.properties.IntProperty;
import crewx.util.RenderUtil;
import crewx.util.TeamUtil;
import crewx.mixin.IAccessorRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Backtrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty latency = new IntProperty("latency", 100, 0, 500);
    public final IntProperty targetFlushDelay = new IntProperty("target-flush-delay", 1000, 100, 1000);
    public final BooleanProperty lineBox = new BooleanProperty("line-box", true);
    public final BooleanProperty filledBox = new BooleanProperty("filled-box", false);
    public final FloatProperty fillOpacity = new FloatProperty("fill-opacity", 45.0F, 20.0F, 100.0F);

    private final Object queueLock = new Object();
    private final List<QueuedPacket> queuedPackets = new ArrayList<QueuedPacket>();
    private final Map<Integer, ServerPosition> serverPositions = new ConcurrentHashMap<Integer, ServerPosition>();

    private volatile int activeTargetId = -1;
    private volatile int pendingTargetId = -1;
    private volatile boolean delayingPackets;
    private volatile ServerPosition lastTargetSnapshot;
    private volatile long lastAttack;
    private volatile long targetPositionUpdatedAt;

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
        super("BackTrack", false);
        this.setKey(Keyboard.KEY_NONE);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.latency.getValue() + "ms"};
    }

    @EventTarget(Priority.HIGHEST)
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || event.getTarget() == null || !(event.getTarget() instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase target = (EntityLivingBase) event.getTarget();
        if (target != mc.thePlayer && this.isValidTarget(target)) {
            this.lastAttack = System.currentTimeMillis();
            int entityId = target.getEntityId();
            if (entityId != this.activeTargetId && entityId != this.pendingTargetId) {
                this.pendingTargetId = entityId;
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onReceivePacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }

        this.processDuePackets(System.currentTimeMillis());
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
            this.flushQueuedPackets();
            this.activeTargetId = -1;
            this.pendingTargetId = -1;
            return;
        }

        this.expireTargetIfNeeded(System.currentTimeMillis());
        this.promotePendingTarget();
        Packet<?> packet = event.getPacket();
        if (this.shouldFlushForPacket(packet)) {
            this.flushQueuedPackets();
            return;
        }

        ServerPosition snapshot = this.processTrackedPacket(packet);
        if (this.activeTargetId == -1) {
            if (this.hasQueuedPackets()) {
                this.flushQueuedPackets();
            }
            return;
        }

        if (snapshot != null) {
            this.delayingPackets = this.latency.getValue() > 0 && this.shouldDelaySnapshot(snapshot);
            this.lastTargetSnapshot = snapshot;
        }

        if (this.delayingPackets && this.latency.getValue() > 0) {
            event.setCancelled(true);
            this.queuePacket(packet, System.currentTimeMillis());
        } else if (this.hasQueuedPackets()) {
            this.flushQueuedPackets();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        long now = System.currentTimeMillis();
        this.processDuePackets(now);
        this.expireTargetIfNeeded(now);
        if (this.interpolationSteps <= 0 || this.activeTargetId == -1) {
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

    @EventTarget(Priority.MEDIUM)
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || (!this.lineBox.getValue() && !this.filledBox.getValue()) || this.activeTargetId == -1 || mc.theWorld == null) {
            return;
        }
        Entity entity = mc.theWorld.getEntityByID(this.activeTargetId);
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase target = (EntityLivingBase) entity;
        float partialTicks = event.getPartialTicks();
        double x = RenderUtil.lerpDouble(this.currentRenderX, this.previousRenderX, partialTicks);
        double y = RenderUtil.lerpDouble(this.currentRenderY, this.previousRenderY, partialTicks);
        double z = RenderUtil.lerpDouble(this.currentRenderZ, this.previousRenderZ, partialTicks);
        double halfWidth = target.width / 2.0D;
        double border = target.getCollisionBorderSize();
        AxisAlignedBB box = new AxisAlignedBB(
                x - halfWidth - border,
                y - border,
                z - halfWidth - border,
                x + halfWidth + border,
                y + target.height + border,
                z + halfWidth + border
        );
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        box = box.offset(-renderManager.getRenderPosX(), -renderManager.getRenderPosY(), -renderManager.getRenderPosZ());

        Color color = GuiModule.getAccent();
        float progress = this.targetPositionUpdatedAt == 0L
                ? 1.0F
                : Math.min(1.0F, (System.currentTimeMillis() - this.targetPositionUpdatedAt) / 200.0F);
        RenderUtil.enableRenderState();
        if (this.lineBox.getValue()) {
            RenderUtil.drawBoundingBox(box, color.getRed(), color.getGreen(), color.getBlue(), Math.round(220.0F * progress), 1.6F);
        }
        if (this.filledBox.getValue()) {
            int alpha = Math.max(0, Math.min(255, Math.round(this.fillOpacity.getValue() * 2.55F * progress)));
            this.drawFilledBox(box, color, alpha);
        }
        RenderUtil.disableRenderState();
    }

    @EventTarget(Priority.MEDIUM)
    public void onLoadWorld(LoadWorldEvent event) {
        this.clearState();
    }

    @Override
    public void onEnabled() {
        this.clearState();
    }

    @Override
    public void onDisabled() {
        this.flushQueuedPackets();
        this.clearState();
    }

    private synchronized void expireTargetIfNeeded(long now) {
        long attackTime = this.lastAttack;
        if (attackTime == 0L || now - attackTime < this.targetFlushDelay.getValue()) {
            return;
        }
        if (this.activeTargetId == -1 && this.pendingTargetId == -1) {
            return;
        }
        if (attackTime != this.lastAttack) {
            return;
        }
        int targetId = this.activeTargetId;
        this.activeTargetId = -1;
        this.pendingTargetId = -1;
        this.lastTargetSnapshot = null;
        this.lastAttack = 0L;
        this.delayingPackets = false;
        if (targetId != -1) {
            this.serverPositions.remove(targetId);
        }
        this.flushQueuedPackets();
        this.resetRenderPosition(0.0D, 0.0D, 0.0D);
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == null || entity.isDead) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (TeamUtil.isSameTeam(player)) {
                return false;
            }
            AntiBot antiBot = (AntiBot) CrewX.moduleManager.modules.get(AntiBot.class);
            return antiBot == null || !antiBot.isEnabled() || !antiBot.isBot(player);
        }
        return true;
    }

    private boolean shouldFlushForPacket(Packet<?> packet) {
        if (packet instanceof S08PacketPlayerPosLook) {
            return true;
        }
        if (packet instanceof S12PacketEntityVelocity && mc.thePlayer != null) {
            return ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId();
        }
        return false;
    }

    private void promotePendingTarget() {
        int pending = this.pendingTargetId;
        if (pending == -1) {
            return;
        }
        this.activeTargetId = pending;
        this.pendingTargetId = -1;
        this.lastTargetSnapshot = null;
        ServerPosition position = this.serverPositions.get(this.activeTargetId);
        Entity entity = mc.theWorld == null ? null : mc.theWorld.getEntityByID(this.activeTargetId);
        if (position != null) {
            this.resetRenderPosition(position.x, position.y, position.z);
        } else if (entity != null) {
            this.resetRenderPosition(entity.posX, entity.posY, entity.posZ);
        } else {
            this.resetRenderPosition(0.0D, 0.0D, 0.0D);
        }
        this.delayingPackets = false;
        this.flushQueuedPackets();
    }

    private ServerPosition processTrackedPacket(Packet<?> packet) {
        if (packet instanceof S13PacketDestroyEntities) {
            for (int entityId : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                this.serverPositions.remove(entityId);
                if (entityId == this.activeTargetId) {
                    this.activeTargetId = -1;
                    this.lastTargetSnapshot = null;
                    this.delayingPackets = false;
                    this.flushQueuedPackets();
                    this.resetRenderPosition(0.0D, 0.0D, 0.0D);
                }
                if (entityId == this.pendingTargetId) {
                    this.pendingTargetId = -1;
                }
            }
            return null;
        }

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
                position = new ServerPosition(
                        previous.x + movement.func_149062_c() / 32.0D,
                        previous.y + movement.func_149061_d() / 32.0D,
                        previous.z + movement.func_149064_e() / 32.0D
                );
                this.serverPositions.put(entityId, position);
            }
        } else if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport = (S18PacketEntityTeleport) packet;
            entityId = teleport.getEntityId();
            position = new ServerPosition(teleport.getX() / 32.0D, teleport.getY() / 32.0D, teleport.getZ() / 32.0D);
            this.serverPositions.put(entityId, position);
        }

        if (entityId == this.activeTargetId) {
            if (position == null) {
                position = this.serverPositions.get(entityId);
            }
            if (position != null) {
                this.setTargetRenderPosition(position.x, position.y, position.z);
                return position;
            }
        }
        return null;
    }

    private boolean shouldDelaySnapshot(ServerPosition snapshot) {
        ServerPosition previous = this.lastTargetSnapshot;
        if (previous == null || mc.thePlayer == null) {
            return false;
        }
        double previousDistance = this.rayIntersectionDistance(previous);
        double newDistance = this.rayIntersectionDistance(snapshot);
        return newDistance > previousDistance + 0.001D;
    }

    private double rayIntersectionDistance(ServerPosition target) {
        if (mc.thePlayer == null) {
            return 0.0D;
        }
        Vec3 eye = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + 1.62D, mc.thePlayer.posZ);
        double dx = target.x - mc.thePlayer.posX;
        double dy = target.y - mc.thePlayer.posY;
        double dz = target.z - mc.thePlayer.posZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-7D) {
            return 0.0D;
        }
        Vec3 end = eye.addVector(dx / length * 1000000.0D, dy / length * 1000000.0D, dz / length * 1000000.0D);
        AxisAlignedBB targetBox = new AxisAlignedBB(
                target.x - 0.3D, target.y, target.z - 0.3D,
                target.x + 0.3D, target.y + 1.8D, target.z + 0.3D
        ).expand(0.1D, 0.1D, 0.1D);
        MovingObjectPosition hit = targetBox.calculateIntercept(eye, end);
        return hit == null ? 0.0D : eye.distanceTo(hit.hitVec);
    }

    private void setTargetRenderPosition(double x, double y, double z) {
        this.targetRenderX = x;
        this.targetRenderY = y;
        this.targetRenderZ = z;
        this.targetPositionUpdatedAt = System.currentTimeMillis();
        this.interpolationSteps = 3;
    }

    private void resetRenderPosition(double x, double y, double z) {
        this.previousRenderX = x;
        this.previousRenderY = y;
        this.previousRenderZ = z;
        this.currentRenderX = x;
        this.currentRenderY = y;
        this.currentRenderZ = z;
        this.targetRenderX = x;
        this.targetRenderY = y;
        this.targetRenderZ = z;
        this.targetPositionUpdatedAt = System.currentTimeMillis();
        this.interpolationSteps = 0;
    }

    private void drawFilledBox(AxisAlignedBB box, Color color, int alpha) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        addVertex(renderer, box.minX, box.minY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.minY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.minY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.minY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.maxY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.maxY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.maxY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.minY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.maxY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.maxY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.minY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.minY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.maxY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.minY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.minY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.maxY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.maxY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.minX, box.minY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.minY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.maxY, box.minZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.maxY, box.maxZ, red, green, blue, alpha);
        addVertex(renderer, box.maxX, box.minY, box.maxZ, red, green, blue, alpha);
        tessellator.draw();
    }

    private static void addVertex(WorldRenderer renderer, double x, double y, double z, int red, int green, int blue, int alpha) {
        renderer.pos(x, y, z).color(red, green, blue, alpha).endVertex();
    }

    private void queuePacket(Packet<?> packet, long now) {
        int maximumDelay = this.latency.getValue();
        long releaseAt = now + ThreadLocalRandom.current().nextInt(1, maximumDelay + 1);
        synchronized (this.queueLock) {
            if (!this.queuedPackets.isEmpty()) {
                long lastRelease = this.queuedPackets.get(this.queuedPackets.size() - 1).releaseAt;
                if (releaseAt <= lastRelease) {
                    releaseAt = lastRelease + 1L;
                }
            }
            this.queuedPackets.add(new QueuedPacket(packet, releaseAt));
        }
    }

    private void processDuePackets(long now) {
        List<Packet<?>> ready = new ArrayList<Packet<?>>();
        synchronized (this.queueLock) {
            while (!this.queuedPackets.isEmpty() && this.queuedPackets.get(0).releaseAt <= now) {
                ready.add(this.queuedPackets.remove(0).packet);
            }
        }
        for (Packet<?> packet : ready) {
            this.processPacketSilently(packet);
        }
    }

    private boolean hasQueuedPackets() {
        synchronized (this.queueLock) {
            return !this.queuedPackets.isEmpty();
        }
    }

    private void flushQueuedPackets() {
        List<Packet<?>> queued = new ArrayList<Packet<?>>();
        synchronized (this.queueLock) {
            for (QueuedPacket packet : this.queuedPackets) {
                queued.add(packet.packet);
            }
            this.queuedPackets.clear();
        }
        for (Packet<?> packet : queued) {
            this.processPacketSilently(packet);
        }
        this.delayingPackets = false;
    }

    @SuppressWarnings("unchecked")
    private void processPacketSilently(Packet<?> packet) {
        try {
            if (mc.getNetHandler() != null) {
                ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
            }
        } catch (Exception ignored) {
        }
    }

    private void clearState() {
        synchronized (this.queueLock) {
            this.queuedPackets.clear();
        }
        this.serverPositions.clear();
        this.activeTargetId = -1;
        this.pendingTargetId = -1;
        this.lastTargetSnapshot = null;
        this.lastAttack = 0L;
        this.delayingPackets = false;
        this.resetRenderPosition(0.0D, 0.0D, 0.0D);
        this.targetPositionUpdatedAt = 0L;
    }

    private static final class QueuedPacket {
        private final Packet<?> packet;
        private final long releaseAt;

        private QueuedPacket(Packet<?> packet, long releaseAt) {
            this.packet = packet;
            this.releaseAt = releaseAt;
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
