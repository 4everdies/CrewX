package crewx.module.modules.player;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.event.types.EventType;
import crewx.events.TickEvent;
import crewx.events.Render2DEvent;
import crewx.gui.BackdropBlur;
import crewx.module.Module;
import crewx.util.ItemUtil;
import crewx.util.KeyBindUtil;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.IntProperty;
import crewx.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public class AutoTool extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter = 0;
    private static volatile int silentToolSlot = -1;
    private static volatile int silentVisualSlot = -1;
    private ItemStack indicatorTool;
    private float indicatorProgress;
    private long indicatorFrameNanos;
    private static final ThreadLocal<int[]> miningSwap = ThreadLocal.withInitial(() -> new int[]{0, -1});
    public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
    public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);
    public final BooleanProperty silent = new BooleanProperty("silent", false);
    public final BooleanProperty toolIndicator = new BooleanProperty("tool-indicator", true);

    public AutoTool() {
        super("AutoTool", false);
    }

    public boolean isKillAura() {
        KillAura killAura = (KillAura) CrewX.moduleManager.modules.get(KillAura.class);
        if (!killAura.isEnabled()) return false;
        return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.silent.getValue()) {
                this.tickSilent();
                return;
            }
            clearSilentMining(true);
            if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
                this.currentToolSlot = -1;
                this.previousSlot = -1;
            }
            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                    && mc.gameSettings.keyBindAttack.isKeyDown()
                    && !mc.thePlayer.isUsingItem()
                    && !isKillAura()) {
                if (this.tickDelayCounter >= this.switchDelay.getValue()
                        && (!(Boolean) this.sneakOnly.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
                    int slot = ItemUtil.findInventorySlot(
                            mc.thePlayer.inventory.currentItem, mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock()
                    );
                    if (mc.thePlayer.inventory.currentItem != slot) {
                        if (this.previousSlot == -1) {
                            this.previousSlot = mc.thePlayer.inventory.currentItem;
                        }
                        mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
                    }
                }
                this.tickDelayCounter++;
            } else {
                if (this.switchBack.getValue() && this.previousSlot != -1) {
                    mc.thePlayer.inventory.currentItem = this.previousSlot;
                }
                this.currentToolSlot = -1;
                this.previousSlot = -1;
                this.tickDelayCounter = 0;
            }
        }
    }

    private void tickSilent() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            clearSilentMining(true);
            this.tickDelayCounter = 0;
            return;
        }
        if (this.previousSlot != -1 && this.currentToolSlot == mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = this.previousSlot;
        }
        this.currentToolSlot = -1;
        this.previousSlot = -1;
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectType.BLOCK
                || mc.objectMouseOver.getBlockPos() == null || !mc.gameSettings.keyBindAttack.isKeyDown()
                || mc.thePlayer.isUsingItem() || isKillAura()
                || (this.sneakOnly.getValue() && !KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
            clearSilentMining(true);
            this.tickDelayCounter = 0;
            return;
        }
        if (this.tickDelayCounter++ < this.switchDelay.getValue()) return;
        int visible = mc.thePlayer.inventory.currentItem;
        int tool = ItemUtil.findInventorySlot(visible,
                mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock());
        if (tool >= 0 && tool < 9 && tool != visible) {
            silentVisualSlot = visible;
            silentToolSlot = tool;
        } else {
            clearSilentMining(true);
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        boolean canShow = this.isEnabled() && this.silent.getValue() && this.toolIndicator.getValue()
                && silentToolSlot >= 0 && mc.thePlayer != null && mc.theWorld != null
                && mc.currentScreen == null && !mc.gameSettings.hideGUI
                && mc.thePlayer.inventory.currentItem == silentVisualSlot;
        ItemStack tool = canShow ? mc.thePlayer.inventory.getStackInSlot(silentToolSlot) : null;
        long now = System.nanoTime();
        float dt = indicatorFrameNanos == 0L ? 0.0F
                : Math.min(0.05F, Math.max(0.0F, (now - indicatorFrameNanos) / 1_000_000_000.0F));
        indicatorFrameNanos = now;
        float target = tool == null ? 0.0F : 1.0F;
        indicatorProgress += (target - indicatorProgress) * Math.min(1.0F, dt * 13.0F);
        if (tool != null) indicatorTool = tool;
        if (indicatorProgress < 0.01F && target == 0.0F) {
            indicatorProgress = 0.0F;
            indicatorTool = null;
            return;
        }
        if (indicatorTool == null || mc.thePlayer == null || mc.theWorld == null
                || mc.currentScreen != null || mc.gameSettings.hideGUI) return;

        ScaledResolution resolution = new ScaledResolution(mc);
        float x = resolution.getScaledWidth() / 2.0F - 11.0F;
        float y = resolution.getScaledHeight() - 90.0F;
        float eased = indicatorProgress * indicatorProgress * (3.0F - 2.0F * indicatorProgress);
        float scale = Math.max(0.01F, eased);
        float size = 22.0F * scale;
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GlStateManager.pushMatrix();
        try {
            BackdropBlur.drawRoundedBlurOnly(x + 11.0F - size / 2.0F, y + 11.0F - size / 2.0F,
                    size, size, 6.0F * scale, 4.0F, eased);
            GL20.glUseProgram(0);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.translate(x + 11.0F, y + 11.0F, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(indicatorTool, -8, -8);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GL20.glUseProgram(oldProgram);
            GlStateManager.popMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }


    public static void beginSilentMining() {
        int[] swap = miningSwap.get();
        if (swap[0]++ != 0) return;
        swap[1] = -1;
        if (mc.thePlayer == null || silentToolSlot < 0 || silentToolSlot > 8
                || mc.thePlayer.inventory.currentItem != silentVisualSlot
                || mc.thePlayer.inventory.getStackInSlot(silentToolSlot) == null) return;
        swap[1] = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = silentToolSlot;
    }

    public static void endSilentMining() {
        int[] swap = miningSwap.get();
        if (swap[0] <= 0 || --swap[0] != 0) return;
        if (swap[1] >= 0 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = swap[1];
        swap[1] = -1;
    }

    private static void clearSilentMining(boolean syncVisibleSlot) {
        boolean wasActive = silentToolSlot >= 0;
        silentToolSlot = -1;
        silentVisualSlot = -1;
        if (wasActive && syncVisibleSlot && mc.playerController != null && mc.thePlayer != null) {
            mc.playerController.updateController();
        }
    }

    @Override
    public void onDisabled() {
        clearSilentMining(true);
        this.currentToolSlot = -1;
        this.previousSlot = -1;
        this.tickDelayCounter = 0;
    }
}
