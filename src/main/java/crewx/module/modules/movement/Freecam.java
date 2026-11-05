package crewx.module.modules.movement;

import com.mojang.authlib.GameProfile;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.event.types.Priority;
import crewx.events.MoveInputEvent;
import crewx.events.PacketEvent;
import crewx.events.UpdateEvent;
import crewx.module.Module;
import crewx.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C03PacketPlayer;

public final class Freecam extends Module {
    private static final Minecraft MC = Minecraft.getMinecraft();

    public final FloatProperty speed = new FloatProperty("speed", 1.0F, 0.1F, 5.0F);
    private EntityOtherPlayerMP body;
    private boolean wasFlying;
    private boolean flightStateCaptured;

    public Freecam() {
        super("Freecam", false);
    }

    @Override
    public void onEnabled() {
        this.body = null;
        this.flightStateCaptured = false;
        this.ensureBody();
    }

    @Override
    public void onDisabled() {
        if (MC.thePlayer != null && MC.theWorld != null && this.body != null
                && this.body.worldObj == MC.theWorld) {
            MC.thePlayer.setPositionAndRotation(this.body.posX, this.body.posY, this.body.posZ,
                    this.body.rotationYaw, this.body.rotationPitch);
            MC.thePlayer.motionX = MC.thePlayer.motionY = MC.thePlayer.motionZ = 0.0D;
            MC.theWorld.removeEntityFromWorld(this.body.getEntityId());
        }
        if (MC.thePlayer != null && this.flightStateCaptured) {
            MC.thePlayer.capabilities.isFlying = this.wasFlying;
        }
        this.body = null;
        this.flightStateCaptured = false;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || MC.thePlayer == null || MC.theWorld == null) {
            return;
        }
        this.ensureBody();
        if (this.body == null) return;

        if (MC.currentScreen != null) {
            MC.thePlayer.motionX = MC.thePlayer.motionY = MC.thePlayer.motionZ = 0.0D;
            return;
        }
        float forwardInput = (MC.gameSettings.keyBindForward.isKeyDown() ? 1.0F : 0.0F)
                - (MC.gameSettings.keyBindBack.isKeyDown() ? 1.0F : 0.0F);
        float strafeInput = (MC.gameSettings.keyBindLeft.isKeyDown() ? 1.0F : 0.0F)
                - (MC.gameSettings.keyBindRight.isKeyDown() ? 1.0F : 0.0F);
        float verticalInput = (MC.gameSettings.keyBindJump.isKeyDown() ? 1.0F : 0.0F)
                - (MC.gameSettings.keyBindSneak.isKeyDown() ? 1.0F : 0.0F);

        double length = Math.sqrt(forwardInput * forwardInput + strafeInput * strafeInput);
        if (length > 1.0D) {
            forwardInput /= (float) length;
            strafeInput /= (float) length;
        }
        double yaw = Math.toRadians(MC.thePlayer.rotationYaw);
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double dx = (strafeInput * cosYaw - forwardInput * sinYaw) * this.speed.getValue();
        double dz = (forwardInput * cosYaw + strafeInput * sinYaw) * this.speed.getValue();
        double dy = verticalInput * this.speed.getValue();
        MC.thePlayer.setPosition(MC.thePlayer.posX + dx, MC.thePlayer.posY + dy, MC.thePlayer.posZ + dz);
        MC.thePlayer.motionX = MC.thePlayer.motionY = MC.thePlayer.motionZ = 0.0D;
    }

    /** Prevent vanilla input from applying a second movement step to the camera player. */
    @EventTarget(Priority.HIGHEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || MC.thePlayer == null || MC.thePlayer.movementInput == null) return;
        MC.thePlayer.movementInput.moveForward = 0.0F;
        MC.thePlayer.movementInput.moveStrafe = 0.0F;
        MC.thePlayer.movementInput.jump = false;
        MC.thePlayer.movementInput.sneak = false;
        MC.thePlayer.motionX = MC.thePlayer.motionY = MC.thePlayer.motionZ = 0.0D;
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) return;
        if (event.getPacket() instanceof C03PacketPlayer) {
            event.setCancelled(true);
        } else if (event.getPacket() instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction.Action action = ((C0BPacketEntityAction) event.getPacket()).getAction();
            if (action != C0BPacketEntityAction.Action.OPEN_INVENTORY) {
                event.setCancelled(true);
            }
        }
    }

    private void ensureBody() {
        if (MC.thePlayer == null || MC.theWorld == null) return;
        if (this.body != null && this.body.worldObj == MC.theWorld) return;

        this.body = new EntityOtherPlayerMP(MC.theWorld,
                new GameProfile(MC.thePlayer.getUniqueID(), MC.thePlayer.getName()));
        this.body.copyLocationAndAnglesFrom(MC.thePlayer);
        this.body.inventory = MC.thePlayer.inventory;
        MC.theWorld.addEntityToWorld(-69420, this.body);

        if (!this.flightStateCaptured) {
            this.wasFlying = MC.thePlayer.capabilities.isFlying;
            this.flightStateCaptured = true;
        }
        MC.thePlayer.capabilities.isFlying = true;
    }
}
