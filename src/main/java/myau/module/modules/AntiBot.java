package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S20PacketEntityProperties;
import net.minecraft.world.WorldSettings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AntiBot extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private final Map<Integer, Integer> invalidGroundVL = new HashMap<>();
    private final Set<Integer> swungSet = new HashSet<>();
    private final Set<Integer> crittedSet = new HashSet<>();
    private final Set<Integer> attributesSet = new HashSet<>();

    public final IntProperty groundVLThreshold;
    public final IntProperty minTicks;
    public final IntProperty minNameLen;
    public final IntProperty maxNameLen;
    public final BooleanProperty invalidGroundCheck;
    public final BooleanProperty neverSwungCheck;
    public final BooleanProperty neverCrittedCheck;
    public final BooleanProperty noAttributesCheck;
    public final BooleanProperty illegalPitchCheck;
    public final BooleanProperty zeroHealthCheck;
    public final BooleanProperty illegalHealthCheck;
    public final BooleanProperty fakeEntityIdCheck;
    public final BooleanProperty noGameModeCheck;
    public final BooleanProperty tooYoungCheck;
    public final BooleanProperty duplicateProfileCheck;
    public final BooleanProperty badNameCheck;

    public AntiBot() {
        super("AntiBot", true);
        this.groundVLThreshold = new IntProperty("ground-vl-threshold", 10, 1, 100);
        this.minTicks = new IntProperty("min-ticks", 20, 0, 200);
        this.minNameLen = new IntProperty("min-name-len", 3, 1, 16);
        this.maxNameLen = new IntProperty("max-name-len", 16, 1, 32);
        this.invalidGroundCheck = new BooleanProperty("invalid-ground", false);
        this.neverSwungCheck = new BooleanProperty("never-swung", false);
        this.neverCrittedCheck = new BooleanProperty("never-critted", false);
        this.noAttributesCheck = new BooleanProperty("no-attributes", true);
        this.illegalPitchCheck = new BooleanProperty("illegal-pitch", true);
        this.zeroHealthCheck = new BooleanProperty("zero-health", true);
        this.illegalHealthCheck = new BooleanProperty("illegal-health", true);
        this.fakeEntityIdCheck = new BooleanProperty("fake-entity-id", false);
        this.noGameModeCheck = new BooleanProperty("no-gamemode", false);
        this.tooYoungCheck = new BooleanProperty("too-young", false);
        this.duplicateProfileCheck = new BooleanProperty("duplicate-profile", false);
        this.badNameCheck = new BooleanProperty("bad-name", false);
    }

    @Override
    public void onEnabled() {
        this.reset();
    }

    @Override
    public void onDisabled() {
        this.reset();
    }

    public void reset() {
        invalidGroundVL.clear();
        swungSet.clear();
        crittedSet.clear();
        attributesSet.clear();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;

        Packet<?> packet = event.getPacket();
        if (packet == null) return;

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            if (mc.theWorld == null) return;
            net.minecraft.entity.Entity e;
            try { e = p.getEntity(mc.theWorld); } catch (Throwable t) { return; }
            if (e == null) return;
            int id = e.getEntityId();
            int vl = invalidGroundVL.getOrDefault(id, 0);
            boolean moved = e.prevPosY != e.posY;
            if (e.onGround && moved) {
                invalidGroundVL.put(id, vl + 1);
            } else if (!e.onGround && vl > 0) {
                int nv = vl / 2;
                if (nv <= 0) invalidGroundVL.remove(id);
                else invalidGroundVL.put(id, nv);
            }
        } else if (packet instanceof S0BPacketAnimation) {
            S0BPacketAnimation p = (S0BPacketAnimation) packet;
            int t = p.getAnimationType();
            int id = p.getEntityID();
            if (t == 0) swungSet.add(id);
            else if (t == 4 || t == 5) crittedSet.add(id);
        } else if (packet instanceof S20PacketEntityProperties) {
            attributesSet.add(((S20PacketEntityProperties) packet).getEntityId());
        } else if (packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                invalidGroundVL.remove(id);
                swungSet.remove(id);
                crittedSet.remove(id);
                attributesSet.remove(id);
            }
        }
    }

    public boolean isBot(EntityPlayer p) {
        if (p == mc.thePlayer) return false;

        if (this.invalidGroundCheck.getValue() && hasInvalidGround(p)) return true;
        if (this.neverSwungCheck.getValue() && neverSwung(p)) return true;
        if (this.neverCrittedCheck.getValue() && neverCritted(p)) return true;
        if (this.noAttributesCheck.getValue() && noAttributes(p)) return true;
        if (this.illegalPitchCheck.getValue() && illegalPitch(p)) return true;
        if (this.zeroHealthCheck.getValue() && zeroHealth(p)) return true;
        if (this.illegalHealthCheck.getValue() && illegalHealth(p)) return true;
        if (this.fakeEntityIdCheck.getValue() && fakeEntityId(p)) return true;
        if (this.noGameModeCheck.getValue() && noGameMode(p)) return true;
        if (this.tooYoungCheck.getValue() && tooYoung(p)) return true;
        if (this.duplicateProfileCheck.getValue() && duplicateProfile(p)) return true;
        if (this.badNameCheck.getValue() && badName(p)) return true;

        return false;
    }

    public boolean hasInvalidGround(EntityPlayer p) {
        return invalidGroundVL.getOrDefault(p.getEntityId(), 0) >= this.groundVLThreshold.getValue();
    }

    public boolean neverSwung(EntityPlayer p) {
        return !swungSet.contains(p.getEntityId());
    }

    public boolean neverCritted(EntityPlayer p) {
        return !crittedSet.contains(p.getEntityId());
    }

    public boolean noAttributes(EntityPlayer p) {
        return !attributesSet.contains(p.getEntityId());
    }

    public static boolean illegalPitch(EntityPlayer p) {
        return Math.abs(p.rotationPitch) > 90.0F;
    }

    public static boolean zeroHealth(EntityPlayer p) {
        return p.getHealth() <= 0.0F || p.isDead;
    }

    public static boolean illegalHealth(EntityPlayer p) {
        return p.getHealth() > p.getMaxHealth() + 0.01F;
    }

    public static boolean fakeEntityId(EntityPlayer p) {
        int id = p.getEntityId();
        return id < 0 || id > 1_000_000_000;
    }

    public static boolean noGameMode(EntityPlayer p) {
        if (mc.getNetHandler() == null) return false;
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(p.getUniqueID());
        if (info == null) return false;
        WorldSettings.GameType gm = info.getGameType();
        return gm == null || gm == WorldSettings.GameType.NOT_SET;
    }

    public boolean tooYoung(EntityPlayer p) {
        return p.ticksExisted < this.minTicks.getValue();
    }

    public static boolean duplicateProfile(EntityPlayer p) {
        if (mc.theWorld == null) return false;
        java.util.UUID uid = p.getGameProfile().getId();
        if (uid == null) return false;
        int count = 0;
        for (EntityPlayer other : mc.theWorld.playerEntities) {
            if (other == null) continue;
            if (uid.equals(other.getGameProfile().getId())) count++;
            if (count > 1) return true;
        }
        return false;
    }

    public boolean badName(EntityPlayer p) {
        String n = p.getName();
        if (n == null) return true;
        int len = n.length();
        if (len < this.minNameLen.getValue() || len > this.maxNameLen.getValue()) return true;
        for (int i = 0; i < len; i++) {
            char c = n.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || c == '_';
            if (!ok) return true;
        }
        return false;
    }
}