package crewx.module.modules.render;

import crewx.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

public final class FreeLook extends Module {
    private static final Minecraft MC = Minecraft.getMinecraft();

    private float cameraYaw;
    private float cameraPitch;
    private int originalPerspective;
    private boolean cameraInitialized;
    private boolean perspectiveCaptured;
    private boolean changedPerspective;

    public FreeLook() {
        super("FreeLook", false);
        this.setKey(Keyboard.KEY_V);
    }

    @Override
    public void onEnabled() {
        this.cameraInitialized = false;
        this.perspectiveCaptured = false;
        this.changedPerspective = false;
        this.prepare(MC.thePlayer);
    }

    @Override
    public void onDisabled() {
        if (this.perspectiveCaptured && this.changedPerspective && MC.gameSettings != null) {
            MC.gameSettings.thirdPersonView = this.originalPerspective;
        }
        this.cameraInitialized = false;
        this.perspectiveCaptured = false;
        this.changedPerspective = false;
    }

    /** Called before camera input/rendering so activation from a saved config is handled too. */
    public void prepare(EntityPlayerSP player) {
        if (player == null || MC.gameSettings == null) return;

        if (!this.perspectiveCaptured) {
            this.originalPerspective = MC.gameSettings.thirdPersonView;
            this.perspectiveCaptured = true;
            this.changedPerspective = this.originalPerspective == 0;
            if (this.changedPerspective) MC.gameSettings.thirdPersonView = 1;
        }

        if (!this.cameraInitialized) {
            this.cameraYaw = player.rotationYaw;
            this.cameraPitch = player.rotationPitch;
            this.cameraInitialized = true;
        }
    }

    public void applyMouseDelta(EntityPlayerSP player, float yawDelta, float pitchDelta) {
        this.prepare(player);
        if (!this.cameraInitialized) return;

        // Match Entity.setAngles' sensitivity scaling, but keep the real player rotation untouched.
        this.cameraYaw += yawDelta * 0.15F;
        this.cameraPitch = MathHelper.clamp_float(this.cameraPitch + pitchDelta * 0.15F, -90.0F, 90.0F);
    }

    public float getCameraYaw() {
        return this.cameraYaw;
    }

    public float getCameraPitch() {
        return this.cameraPitch;
    }
}
