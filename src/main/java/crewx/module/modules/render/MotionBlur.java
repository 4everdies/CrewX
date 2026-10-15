package crewx.module.modules.render;

import crewx.event.EventTarget;
import crewx.events.Render3DEvent;
import crewx.module.Module;
import crewx.property.properties.PercentProperty;
import crewx.util.MotionBlurRenderer;
import net.minecraft.client.Minecraft;

public final class MotionBlur extends Module {
    private static final Minecraft MC = Minecraft.getMinecraft();

    public final PercentProperty strength = new PercentProperty("strength", 35, 5, 80, null);

    public MotionBlur() {
        super("Motion Blur", false);
    }

    @Override
    public void onEnabled() {
        MotionBlurRenderer.reset();
    }

    @Override
    public void onDisabled() {
        MotionBlurRenderer.release();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || MC.theWorld == null || MC.thePlayer == null) return;
        float alpha = this.strength.getValue() / 100.0F * 0.65F;
        MotionBlurRenderer.render(alpha);
    }
}
