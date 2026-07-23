package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.DoubleProperty; 
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final PercentProperty slowDownVelocity = new PercentProperty("SlowDown Velocity", 60); 
    public final PercentProperty slowDownNormal = new PercentProperty("SlowDown Normal", 60);
    
    public final DoubleProperty bufferDecrease = new DoubleProperty("Buffer Decrease", 1.0, 0.1, 10.0);
    public final DoubleProperty maxBuffer = new DoubleProperty("Max Buffer", 5.0, 1.0, 10.0);
    
    public final BooleanProperty sprintSlowDownVelocity = new BooleanProperty("Velocity Hit Sprint", false);
    public final BooleanProperty sprintSlowDownNormal = new BooleanProperty("Normal Hit Sprint", false);
    public final BooleanProperty bufferAbuse = new BooleanProperty("Buffer Abuse", false);
    public final BooleanProperty onlyInAir = new BooleanProperty("Only In Air", false);

    private boolean resetting;
    private double combo;

    public KeepSprint() {
        super("KeepSprint", false);
    }

    public void onAttackHit() {
        if (mc.thePlayer.onGround && this.onlyInAir.getValue()) return;

        if (this.bufferAbuse.getValue()) {
            if (this.combo < this.maxBuffer.getValue() && !this.resetting) {
                this.combo++;
            } else {
                if (this.combo > 0) {
                    this.combo = Math.max(0, this.combo - this.bufferDecrease.getValue());
                    this.resetting = true;
                } else {
                    this.resetting = false;
                }
            }
        } else {
            this.combo = 0;
        }
    }

    public double getCustomSlowdown() {
        if (mc.thePlayer.onGround && this.onlyInAir.getValue()) return 0.2; 
        if (this.resetting && this.combo > 0) return 0.2; 

        if (mc.thePlayer.hurtTime > 0) {
            return this.slowDownVelocity.getValue() / 100.0;
        } else {
            return this.slowDownNormal.getValue() / 100.0;
        }
    }

    public boolean shouldKeepSprint() {
        if (mc.thePlayer.onGround && this.onlyInAir.getValue()) return false;
        if (this.resetting && this.combo > 0) return false;

        if (mc.thePlayer.hurtTime > 0) {
            return this.sprintSlowDownVelocity.getValue();
        } else {
            return this.sprintSlowDownNormal.getValue();
        }
    }
}
