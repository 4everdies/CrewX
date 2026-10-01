package crewx.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;

public final class BlurController {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final ResourceLocation SOFT_BLUR = new ResourceLocation("crewx", "shaders/post/soft_blur.json");
    private static boolean clickGuiOpen;
    private static ShaderGroup ownedShader;

    private BlurController() {
    }

    public static void setClickGuiOpen(boolean open) {
        clickGuiOpen = open;
        update();
    }

    private static void update() {
        if (MC.entityRenderer == null) return;
        if (!clickGuiOpen) {
            if (ownedShader != null && MC.entityRenderer.getShaderGroup() == ownedShader) {
                MC.entityRenderer.stopUseShader();
            }
            ownedShader = null;
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
