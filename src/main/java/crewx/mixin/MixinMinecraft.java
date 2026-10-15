package crewx.mixin;

import crewx.CrewX;
import crewx.init.Initializer;
import crewx.event.EventManager;
import crewx.event.types.EventType;
import crewx.events.*;
import crewx.module.modules.combat.NoHitDelay;
import crewx.gui.CrewXTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

@SideOnly(Side.CLIENT)
@Mixin(value = {Minecraft.class}, priority = 9999)
public abstract class MixinMinecraft {
    @Shadow
    private int leftClickCounter;
    @Shadow
    public PlayerControllerMP playerController;
    @Shadow
    public WorldClient theWorld;
    @Shadow
    public EntityPlayerSP thePlayer;
    @Shadow
    public GuiScreen currentScreen;

    /** Every vanilla multiplayer screen (title menu, disconnect screens...) becomes the CrewX server selector. */
    @ModifyVariable(method = "displayGuiScreen", at = @At("HEAD"), argsOnly = true)
    private GuiScreen crewx$serverSelector(GuiScreen screen) {
        if (screen != null && screen.getClass() == GuiMultiplayer.class && crewx.gui.InterfaceSettings.isEnabled()) {
            return new crewx.gui.servers.CrewXServerSelector(((IAccessorGuiMultiplayer) screen).getParentScreen());
        }
        return screen;
    }

    @Inject(method = "displayGuiScreen", at = @At("HEAD"))
    private void crewx$displayGuiScreen(GuiScreen screen, CallbackInfo callbackInfo) {
        if (screen != this.currentScreen) {
            if (screen instanceof GuiMainMenu && crewx.gui.InterfaceSettings.isEnabled()) {
                CrewXTransition.start();
            } else {
                CrewXTransition.cancel();
            }
        }
    }

    @Inject(
            method = {"startGame"},
            at = {@At("HEAD")}
    )
    private void startGame(CallbackInfo callbackInfo) {
        new Initializer();
    }

    @Inject(
            method = {"startGame"},
            at = {@At("RETURN")}
    )
    private void postStartGame(CallbackInfo callbackInfo) {
        new CrewX();
        Display.setTitle("CrewX");
        setWindowIcon();
    }

    private static void setWindowIcon() {
        try {
            InputStream stream = MixinMinecraft.class.getClassLoader().getResourceAsStream("assets/crewx/icons/icon.png");
            if (stream == null) return;
            java.awt.image.BufferedImage original = javax.imageio.ImageIO.read(stream);
            stream.close();

            ByteBuffer icon16 = getIconBuffer(original, 16);
            ByteBuffer icon32 = getIconBuffer(original, 32);

            Display.setIcon(new ByteBuffer[]{icon16, icon32});
        } catch (IOException ignored) {
        }
    }

    private static ByteBuffer getIconBuffer(java.awt.image.BufferedImage original, int size) {
        java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, size, size, null);
        g.dispose();

        int[] aint = scaled.getRGB(0, 0, size, size, null, 0, size);
        ByteBuffer buffer = BufferUtils.createByteBuffer(4 * aint.length);
        for (int i : aint) {
            buffer.putInt(i << 8 | i >> 24 & 255);
        }
        buffer.flip();
        return buffer;
    }

    @Inject(method = {"runTick"}, at = {@At("HEAD")})
    private void crewx$tickTimerStart(CallbackInfo callbackInfo) {
        crewx.util.HitchMonitor.beginPhase(crewx.util.HitchMonitor.TICK);
    }

    @Inject(method = {"runTick"}, at = {@At("RETURN")})
    private void crewx$tickTimerEnd(CallbackInfo callbackInfo) {
        crewx.util.HitchMonitor.endPhase(crewx.util.HitchMonitor.TICK);
    }

    @Inject(method = {"updateDisplay"}, at = {@At("HEAD")}, require = 0)
    private void crewx$swapTimerStart(CallbackInfo callbackInfo) {
        crewx.util.HitchMonitor.beginPhase(crewx.util.HitchMonitor.SWAP);
    }

    @Inject(method = {"updateDisplay"}, at = {@At("RETURN")}, require = 0)
    private void crewx$swapTimerEnd(CallbackInfo callbackInfo) {
        crewx.util.HitchMonitor.endPhase(crewx.util.HitchMonitor.SWAP);
    }

    @Inject(
            method = {"runTick"},
            at = {@At("HEAD")}
    )
    private void runTick(CallbackInfo callbackInfo) {
        if (this.theWorld != null && this.thePlayer != null) {
            EventManager.call(new TickEvent(EventType.PRE));
        }
    }

    @Inject(
            method = {"runTick"},
            at = {@At("RETURN")}
    )
    private void postRunTick(CallbackInfo callbackInfo) {
        if (this.theWorld != null && this.thePlayer != null) {
            EventManager.call(new TickEvent(EventType.POST));
        }
    }

    @Inject(
            method = {"loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V"},
            at = {@At("HEAD")}
    )
    private void loadWorld(WorldClient worldClient, String string, CallbackInfo callbackInfo) {
        EventManager.call(new LoadWorldEvent());
    }

    @Inject(
            method = {"updateFramebufferSize"},
            at = {@At("RETURN")}
    )
    private void updateFramebufferSize(CallbackInfo callbackInfo) {
        EventManager.call(new ResizeEvent());
    }

    @Inject(
            method = {"clickMouse"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void clickMouse(CallbackInfo callbackInfo) {
        if (CrewX.moduleManager != null && CrewX.moduleManager.modules.get(NoHitDelay.class).isEnabled()) {
            this.leftClickCounter = 0;
        }
        LeftClickMouseEvent event = new LeftClickMouseEvent();
        EventManager.call(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = {"rightClickMouse"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void rightClickMouse(CallbackInfo callbackInfo) {
        RightClickMouseEvent event = new RightClickMouseEvent();
        EventManager.call(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = {"sendClickBlockToController"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void sendClickBlockToController(CallbackInfo callbackInfo) {
        HitBlockEvent event = new HitBlockEvent();
        EventManager.call(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
            this.playerController.resetBlockRemoving();
        }
    }

    @Redirect(
            method = {"runTick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/settings/KeyBinding;setKeyBindState(IZ)V"
            )
    )
    private void setKeyBindState(int integer, boolean boolean2) {
        KeyBinding.setKeyBindState(integer, boolean2);
        if (boolean2 && this.currentScreen == null) {
            EventManager.call(new KeyEvent(integer));
        }
    }

    @Redirect(
            method = {"runTick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;changeCurrentItem(I)V"
            )
    )
    private void changeCurrentItem(InventoryPlayer inventoryPlayer, int slot) {
        SwapItemEvent event = new SwapItemEvent(-1, slot);
        EventManager.call(event);
        if (!event.isCancelled()) {
            inventoryPlayer.changeCurrentItem(slot);
        }
    }
}