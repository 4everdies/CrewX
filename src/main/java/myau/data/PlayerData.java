package myau.data;

import net.minecraft.entity.player.EntityPlayer;

public class PlayerData {
    public int autoBlockTicks = 0;
    public int sneakTicks = 0;
    public int noSlowTicks = 0;
    public double speed = 0;
    public int fastTick = 0;
    public int lastSneakTick = 0;
    public int aboveVoidTicks = 0;
    public double serverPosX = 0;
    public double serverPosY = 0;
    public double serverPosZ = 0;

    public void update(EntityPlayer player) {
        double dx = player.posX - player.lastTickPosX;
        double dz = player.posZ - player.lastTickPosZ;
        speed = Math.sqrt(dx * dx + dz * dz);

        if (speed > 0.1) {
            fastTick++;
        } else {
            fastTick = 0;
        }

        if (player.isBlocking()) {
            autoBlockTicks++;
        } else {
            autoBlockTicks = 0;
        }

        if (player.isUsingItem() && speed > 0.08) {
            noSlowTicks++;
        } else {
            noSlowTicks = 0;
        }

        if (player.posY < 0) {
            aboveVoidTicks++;
        } else {
            aboveVoidTicks = 0;
        }
    }

    public void updateServerPos(EntityPlayer player) {
        serverPosX = (double) player.serverPosX / 32.0;
        serverPosY = (double) player.serverPosY / 32.0;
        serverPosZ = (double) player.serverPosZ / 32.0;
    }

    public void updateSneak(EntityPlayer player) {
        if (player.isSneaking()) {
            sneakTicks++;
        } else {
            if (sneakTicks > 0) {
                lastSneakTick = player.ticksExisted;
            }
            sneakTicks = 0;
        }
    }
}
