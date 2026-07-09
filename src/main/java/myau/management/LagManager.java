package myau.management;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.Vec3;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class LagManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final Deque<LagPacket> packetQueue;
    private int tickDelay;
    private boolean flushing;
    private Vec3 lastPosition;

    public LagManager() {
        this.packetQueue = new ConcurrentLinkedDeque<>();
        this.tickDelay = 0;
        this.flushing = false;
        this.lastPosition = new Vec3(0.0, 0.0, 0.0);
    }

    private static final int MAX_FLUSH_PER_TICK = 4;

    private void flushQueue() {
        if (mc.getNetHandler() == null) {
            this.packetQueue.clear();
            return;
        }
        this.flushing = true;
        int sent = 0;
        while (!this.packetQueue.isEmpty()) {
            LagPacket lagPacket = this.packetQueue.peek();
            if (this.tickDelay > 0 && lagPacket.delay <= this.tickDelay) {
                break;
            }
            if (this.tickDelay > 0 && sent >= MAX_FLUSH_PER_TICK) {
                break;
            }
            this.packetQueue.poll();
            PacketUtil.sendPacketNoEvent(lagPacket.packet);
            if (lagPacket.packet instanceof C03PacketPlayer) {
                C03PacketPlayer c03 = (C03PacketPlayer) lagPacket.packet;
                if (c03.isMoving()) {
                    this.lastPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
                }
            }
            sent++;
        }
        this.flushing = false;
    }


    private void incrementDelays() {
        this.packetQueue.forEach(z -> z.delay++);
    }

    public boolean handlePacket(Packet<?> packet) {
        this.flushQueue();
        if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage
                || packet instanceof net.minecraft.network.play.client.C0FPacketConfirmTransaction
                || packet instanceof net.minecraft.network.play.client.C0BPacketEntityAction) {
            return false;
        } else if ((long) this.tickDelay > 0L) {
            this.packetQueue.offer(new LagPacket(packet));
            return true;
        } else {
            if (packet instanceof C03PacketPlayer) {
                C03PacketPlayer c03 = (C03PacketPlayer) packet;
                if (c03.isMoving()) {
                    this.lastPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
                }
            }
            return false;
        }
    }

    public void setDelay(int delay) {
        this.tickDelay = delay;
    }

    public Vec3 getLastPosition() {
        return this.lastPosition;
    }

    public boolean isFlushing() {
        return this.flushing;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) {
                this.packetQueue.clear();
                this.setDelay(0);
                return;
            }
            this.incrementDelays();
            this.flushQueue();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.setDelay(0);
        }
    }

    public static class LagPacket {
        public final Packet<?> packet;
        public int delay;

        public LagPacket(Packet<?> packet) {
            this.packet = packet;
            this.delay = 0;
        }
    }
}
