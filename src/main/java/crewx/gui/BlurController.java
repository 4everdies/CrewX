package crewx.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;

public final class BlurController {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final ResourceLocation SOFT_BLUR = new ResourceLocation("crewx", "shaders/post/soft_blur.json");
    private static boolean clickGuiOpen;
    private static ShaderGroup ownedShader;
    private static long retryAfter;

    private BlurController() {
    }

    public static void setClickGuiOpen(boolean open) {
        clickGuiOpen = open;
        retryAfter = 0L;
        update();
    }

    public static void ensureBlur() {
        if (!clickGuiOpen || System.currentTimeMillis() < retryAfter) return;
        retryAfter = System.currentTimeMillis() + 1000L;
        update();
    }

    private static void update() {
        if (MC.entityRenderer == null) return;
        if (!clickGuiOpen) {
            if (ownedShader != null && MC.entityRenderer.getShaderGroup() == ownedShader) {
                MC.entityRenderer.stopUseShader();
            }
            ownedShader = null;
            retryAfter = 0L;
            return;
        }
        if (MC.theWorld == null || MC.thePlayer == null || MC.entityRenderer.isShaderActive()) return;
        try {
            MC.entityRenderer.loadShader(SOFT_BLUR);
            if (MC.entityRenderer.isShaderActive()) {
                ownedShader = MC.entityRenderer.getShaderGroup();
            }
        } catch (Throwable ignored) {
            ownedShader = null;
        }
    }
}
