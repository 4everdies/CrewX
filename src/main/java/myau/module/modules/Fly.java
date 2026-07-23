package myau.module.modules;

import myau.Myau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.StrafeEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.util.KeyBindUtil;
import myau.util.MoveUtil;
import myau.util.PacketUtil;
import myau.property.properties.FloatProperty;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;

public class Fly extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private double verticalMotion = 0.0;
    private boolean isKaizenMode = false;
    private boolean isBlinkActive = false;
    private long disableTimer = 0L;
    private boolean waitingToDisableBlink = false;
    private boolean isFlyDisabled = false;

    private long enableTimer = 0L;
    private boolean waitingToEnableFly = false;
    private boolean isFlyPhysicallyEnabled = false;

    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 1.0F, 0.0F, 100.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 1.0F, 0.0F, 100.0F);
    public final ModeProperty flyMode = new ModeProperty("mode", 0, new String[]{"Normal", "Kaizen"});

    public Fly() {
        super("Fly", false);
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (isKaizenMode && !isFlyPhysicallyEnabled) {
                return;
            }
            if (mc.thePlayer.posY % 1.0 != 0.0) {
                mc.thePlayer.motionY = this.verticalMotion;
            }
            MoveUtil.setSpeed(0.0);
            event.setFriction((float) MoveUtil.getBaseMoveSpeed() * this.hSpeed.getValue());
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.isEnabled() && isKaizenMode && waitingToEnableFly) {
                if (System.currentTimeMillis() - enableTimer >= 20L) {
                    isFlyPhysicallyEnabled = true;
                    waitingToEnableFly = false;
                }
            }

            if (waitingToDisableBlink) {
                if (System.currentTimeMillis() - disableTimer >= 1000L) {
                    Myau.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                    isBlinkActive = false;
                    waitingToDisableBlink = false;
                    isFlyDisabled = false;
                }
            }

            if (this.isEnabled()) {
                if (isKaizenMode && !isFlyPhysicallyEnabled) {
                    return;
                }
                this.verticalMotion = 0.0;
                if (mc.currentScreen == null) {
                    if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                        this.verticalMotion = this.verticalMotion + this.vSpeed.getValue().doubleValue() * 0.42F;
                    }
                    if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
                        this.verticalMotion = this.verticalMotion - this.vSpeed.getValue().doubleValue() * 0.42F;
                    }
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
                }
            }
        }
    }

    @Override
    public void onEnabled() {
        isKaizenMode = flyMode.getValue() == 1;
        waitingToDisableBlink = false;
        disableTimer = 0L;
        isFlyDisabled = false;

        PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SNEAKING));
        PacketUtil.sendPacket(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));

        if (isKaizenMode) {
            Myau.blinkManager.setBlinkState(true, BlinkModules.BLINK);
            isBlinkActive = true;
            isFlyPhysicallyEnabled = false;
            waitingToEnableFly = true;
            enableTimer = System.currentTimeMillis();
        } else {
            isFlyPhysicallyEnabled = true;
            waitingToEnableFly = false;
        }
    }

    @Override
    public void onDisabled() {
        isFlyPhysicallyEnabled = false;
        waitingToEnableFly = false;

        if (isKaizenMode && isBlinkActive && !waitingToDisableBlink) {
            isFlyDisabled = true;
            waitingToDisableBlink = true;
            disableTimer = System.currentTimeMillis();
        } else if (!isKaizenMode) {
            mc.thePlayer.motionY = 0.0;
            MoveUtil.setSpeed(0.0);
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
        }
    }

    public void disableFlyAndBlink() {
        isFlyPhysicallyEnabled = false;
        waitingToEnableFly = false;

        if (isKaizenMode && isBlinkActive && !waitingToDisableBlink) {
            this.setEnabled(false);
            isFlyDisabled = true;
            waitingToDisableBlink = true;
            disableTimer = System.currentTimeMillis();
        } else {
            this.setEnabled(false);
        }
    }

    public boolean isKaizenMode() {
        return isKaizenMode;
    }

    public boolean isBlinkActive() {
        return isBlinkActive;
    }
}

