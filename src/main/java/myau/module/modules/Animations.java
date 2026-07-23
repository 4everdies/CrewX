package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

public final class Animations extends Module {
    private static final float PI = (float) Math.PI;

    public final ModeProperty blockAnimation = new ModeProperty("Block Animation", 0, new String[]{
            "None", "1.7", "Sunny", "Lucid", "Astro", "Smooth", "Spin", "Leaked", "Old",
            "Exhibition", "Exhibition Old", "Exhibition New", "Swong", "Stella", "Flup", "Noov",
            "Komorebi", "Rhys", "Swing", "?", "Stab", "Beta", "Dortware", "Avatar", "Tap"
    });
    public final ModeProperty swingAnimation = new ModeProperty("Swing Animation", 0, new String[]{
            "None", "Punch", "Shove", "Smooth", "1.9+"
    });
    public final BooleanProperty onlyWhenBlocking = new BooleanProperty("Update Position Only When Blocking", true);
    public final IntProperty swingSpeed = new IntProperty("Swing Speed", 1, -200, 50);
    public final FloatProperty x = new FloatProperty("X", 0.0F, -2.0F, 2.0F);
    public final FloatProperty y = new FloatProperty("Y", 0.0F, -2.0F, 2.0F);
    public final FloatProperty z = new FloatProperty("Z", 0.0F, -2.0F, 2.0F);
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.1F, 2.0F);
    public final BooleanProperty alwaysShow = new BooleanProperty("Always Show", false);

    public Animations() {
        super("Animations", false);
    }

    public void translatePosition() {
        GlStateManager.translate(this.x.getValue(), this.y.getValue(), this.z.getValue());
    }

    public int adjustSwingDuration(int vanillaDuration) {
        float multiplier = (-this.swingSpeed.getValue() / 100.0F) + 1.0F;
        return Math.max(1, (int) (vanillaDuration * multiplier));
    }

    public void applyFirstPersonTransform(boolean blocking, float equipProgress, float swingProgress,
                                          float rendererEquippedProgress, boolean sneaking) {
        if (blocking) {
            this.translatePosition();
            this.applyBlockTransform(equipProgress, swingProgress, rendererEquippedProgress, sneaking);
        } else {
            if (!this.onlyWhenBlocking.getValue()) {
                this.translatePosition();
            }
            this.applySwingTransform(equipProgress, swingProgress);
        }
    }

    private void applyBlockTransform(float equipProgress, float swingProgress,
                                     float rendererEquippedProgress, boolean sneaking) {
        double configuredScale = this.scale.getValue();
        float animationProgress = this.alwaysShow.getValue() ? 0.0F : equipProgress;
        float converted = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * PI);

        switch (this.blockAnimation.getModeString()) {
            case "1.7":
                transformFirstPersonItem(animationProgress, swingProgress);
                scale(configuredScale);
                blockTransformation();
                break;

            case "Sunny":
                configuredScale = 0.99D;
                GlStateManager.translate(0.05F, -0.05F, -0.12F);
                transformFirstPersonItem(animationProgress + 0.15F, swingProgress);
                scale(configuredScale);
                blockTransformation();
                GlStateManager.translate(-0.5F, 0.2F, 0.0F);
                break;

            case "Lucid":
                transformFirstPersonItem(animationProgress - 0.1F, swingProgress);
                scale(configuredScale);
                blockTransformation();
                break;

            case "Astro":
                GlStateManager.translate(0.0F, 0.03F, -0.05F);
                transformFirstPersonItem(animationProgress / 2.0F, swingProgress);
                scale(configuredScale);
                GlStateManager.rotate(converted * 15.0F, -converted, 0.0F, 9.0F);
                GlStateManager.rotate(converted * 40.0F, 1.0F, -converted / 2.0F, 0.0F);
                blockTransformation();
                break;

            case "Tap": {
                GL11.glTranslatef(0.0F, 0.3F, 0.0F);
                float smooth = swingProgress * 0.8F - swingProgress * swingProgress * 0.8F;
                scale(configuredScale);
                GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(smooth * -90.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.scale(0.37F, 0.37F, 0.37F);
                blockTransformation();
                break;
            }

            case "Beta": {
                GL11.glTranslatef(0.0F, 0.3F, 0.0F);
                float curve = MathHelper.sin(swingProgress * swingProgress * PI);
                transformFirstPersonItem(rendererEquippedProgress * 0.5F, 0.0F);
                scale(configuredScale);
                GlStateManager.rotate(-curve * 27.5F, -8.0F, 0.0F, 9.0F);
                GlStateManager.rotate(-curve * 45.0F, 1.0F, curve / 2.0F, 0.0F);
                blockTransformation();
                GL11.glTranslated(1.2D, 0.3D, 0.5D);
                GL11.glTranslatef(-1.0F, sneaking ? -0.1F : -0.2F, 0.2F);
                break;
            }

            case "Avatar": {
                GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                float curve = MathHelper.sin(swingProgress * swingProgress * PI);
                float rootCurve = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * PI);
                GlStateManager.rotate(curve * -20.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(rootCurve * -20.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(rootCurve * -40.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.4F, 0.4F, 0.4F);
                blockTransformation();
                break;
            }

            case "Smooth": {
                transformFirstPersonItem(animationProgress, 0.0F);
                scale(configuredScale);
                float offset = -converted * 2.0F;
                GlStateManager.translate(0.0F, offset / 10.0F + 0.1F, 0.0F);
                GlStateManager.rotate(offset * 10.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(250.0F, 0.2F, 1.0F, -0.6F);
                GlStateManager.rotate(-10.0F, 1.0F, 0.5F, 1.0F);
                GlStateManager.rotate(-offset * 20.0F, 1.0F, 0.5F, 1.0F);
                break;
            }

            case "Stab":
                GlStateManager.translate(0.6F, 0.3F, -0.6F - converted * 0.7F);
                GlStateManager.rotate(6090.0F, 0.0F, 0.0F, 0.1F);
                GlStateManager.rotate(6085.0F, 0.0F, 0.1F, 0.0F);
                GlStateManager.rotate(6110.0F, 0.1F, 0.0F, 0.0F);
                transformFirstPersonItem(0.0F, 0.0F);
                scale(configuredScale);
                blockTransformation();
                break;

            case "Spin":
                transformFirstPersonItem(animationProgress, 0.0F);
                scale(configuredScale);
                GlStateManager.translate(0.0F, 0.2F, -1.0F);
                GlStateManager.rotate(-59.0F, -1.0F, 0.0F, 3.0F);
                GlStateManager.rotate(-(System.currentTimeMillis() / 2L % 360L), 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
                break;

            case "Leaked":
                GlStateManager.translate(0.0F, -0.03F, -0.13F);
                transformFirstPersonItem(animationProgress / 3.0F, 0.0F);
                scale(configuredScale);
                GlStateManager.translate(0.0F, 0.1F, 0.0F);
                blockTransformation();
                GlStateManager.rotate(converted * 10.0F, 0.0F, 1.0F, 1.5F);
                GlStateManager.rotate(-converted * 50.0F, 1.0F, 0.9F, 0.0F);
                break;

            case "Old":
                GlStateManager.translate(0.0F, 0.1F, 0.0F);
                transformFirstPersonItem(animationProgress / 2.0F - 0.2F, swingProgress);
                scale(configuredScale);
                blockTransformation();
                break;

            case "Exhibition":
                GlStateManager.translate(0.0F, -0.05F, 0.0F);
                transformFirstPersonItem(animationProgress / 2.0F, 0.0F);
                scale(configuredScale);
                GlStateManager.translate(0.0F, 0.3F, 0.0F);
                GlStateManager.rotate(-converted * 31.0F, 1.0F, 0.0F, 2.0F);
                GlStateManager.rotate(-converted * 33.0F, 1.5F, converted / 1.1F, 0.0F);
                blockTransformation();
                break;

            case "Exhibition Old":
                GlStateManager.translate(0.0F, -0.05F, 0.0F);
                GlStateManager.translate(-0.04F, 0.13F, 0.0F);
                transformFirstPersonItem(animationProgress / 2.5F, 0.0F);
                scale(configuredScale);
                GlStateManager.rotate(-converted * 20.0F, converted / 2.0F, 1.0F, 4.0F);
                GlStateManager.rotate(-converted * 30.0F, 1.0F, converted / 3.0F, 0.0F);
                blockTransformation();
                break;

            case "Exhibition New":
                GlStateManager.translate(0.0F, -0.04F, -0.01F);
                transformFirstPersonItem(animationProgress / 2.0F, 0.0F);
                scale(configuredScale);
                GlStateManager.translate(0.0F, 0.3F, 0.0F);
                GlStateManager.rotate(-converted * 30.0F, 1.0F, 0.0F, 2.0F);
                GlStateManager.rotate(-converted * 44.0F, 1.5F, converted / 1.2F, 0.0F);
                blockTransformation();
                break;

            case "Swong":
                GlStateManager.translate(0.0F, 0.1F, -0.05F);
                transformFirstPersonItem(animationProgress / 2.0F, swingProgress);
                scale(configuredScale);
                GlStateManager.rotate(converted * 30.0F, -converted, 0.0F, 9.0F);
                GlStateManager.rotate(converted * 40.0F, 1.0F, -converted, 0.0F);
                blockTransformation();
                break;

            case "Stella":
                transformFirstPersonItem(-0.1F, swingProgress);
                scale(configuredScale);
                GlStateManager.translate(-0.5F, 0.4F, -0.2F);
                GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(-70.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(40.0F, 0.0F, 1.0F, 0.0F);
                break;

            case "Flup":
                GlStateManager.translate(0.0F, 0.1F, -0.05F);
                transformFirstPersonItem(animationProgress, 0.0F);
                scale(configuredScale);
                blockTransformation();
                GlStateManager.translate(-0.05F, 0.2F, 0.0F);
                GlStateManager.rotate(-converted * 35.0F, -8.0F, 0.0F, 9.0F);
                GlStateManager.rotate(-converted * 70.0F, 1.0F, -0.4F, 0.0F);
                break;

            case "Noov":
                transformFirstPersonItem(animationProgress / 1.5F, 0.0F);
                scale(configuredScale);
                blockTransformation();
                GlStateManager.translate(-0.05F, 0.3F, 0.3F);
                GlStateManager.rotate(-converted * 140.0F, 8.0F, 0.0F, 8.0F);
                GlStateManager.rotate(converted * 20.0F, 8.0F, 0.0F, 8.0F);
                break;

            case "Komorebi":
                transformFirstPersonItem(-0.25F, 1.0F + converted / 10.0F);
                scale(configuredScale);
                GL11.glRotated(-converted * 25.0F, 1.0F, 0.0F, 0.0F);
                blockTransformation();
                break;

            case "Rhys": {
                GlStateManager.translate(0.41F, -0.25F, -0.5555557F);
                GlStateManager.rotate(35.0F, 0.0F, 1.5F, 0.0F);
                float curve = MathHelper.sin(swingProgress * swingProgress / 64.0F * PI);
                GlStateManager.rotate(curve * -5.0F, 0.0F, 0.0F, 0.0F);
                GlStateManager.rotate(converted * -12.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(converted * -65.0F, 1.0F, 0.0F, 0.0F);
                scale(configuredScale);
                blockTransformation();
                break;
            }

            case "Swing":
                transformFirstPersonItem(animationProgress, swingProgress);
                scale(configuredScale);
                blockTransformation();
                GlStateManager.translate(-0.3F, -0.1F, 0.0F);
                break;

            case "?":
                transformFirstPersonItem(animationProgress, swingProgress);
                scale(configuredScale);
                GL11.glTranslatef(-0.35F, 0.1F, 0.0F);
                GL11.glTranslatef(-0.05F, -0.1F, 0.1F);
                blockTransformation();
                break;

            case "Dortware": {
                float curve = MathHelper.sin((float) (swingProgress * swingProgress * Math.PI - 3.0D));
                float rootCurve = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * PI);
                transformFirstPersonItem(animationProgress, 1.0F);
                GlStateManager.rotate(-rootCurve * 10.0F, 0.0F, 15.0F, 200.0F);
                GlStateManager.rotate(-rootCurve * 10.0F, 300.0F, rootCurve / 2.0F, 1.0F);
                blockTransformation();
                GL11.glTranslated(2.4D, 0.3D, 0.5D);
                GL11.glTranslatef(-2.10F, -0.2F, 0.1F);
                GlStateManager.rotate(curve * 13.0F, -10.0F, -1.4F, -10.0F);
                break;
            }

            case "None":
            default:
                transformFirstPersonItem(animationProgress, 0.0F);
                scale(configuredScale);
                blockTransformation();
                break;
        }
    }

    private void applySwingTransform(float equipProgress, float swingProgress) {
        switch (this.swingAnimation.getModeString()) {
            case "Shove":
                transformFirstPersonItem(equipProgress, equipProgress);
                doItemUsedTransformations(swingProgress);
                break;
            case "Smooth":
                transformFirstPersonItem(equipProgress, swingProgress);
                doItemUsedTransformations(equipProgress);
                break;
            case "Punch":
                transformFirstPersonItem(equipProgress, swingProgress);
                doItemUsedTransformations(swingProgress);
                break;
            case "1.9+":
            case "None":
            default:
                doItemUsedTransformations(swingProgress);
                transformFirstPersonItem(equipProgress, swingProgress);
                break;
        }
        if (!this.onlyWhenBlocking.getValue()) {
            scale(this.scale.getValue());
        }
    }

    private static void doItemUsedTransformations(float swingProgress) {
        float x = -0.4F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * PI);
        float y = 0.2F * MathHelper.sin(MathHelper.sqrt_float(swingProgress) * PI * 2.0F);
        float z = -0.2F * MathHelper.sin(swingProgress * PI);
        GlStateManager.translate(x, y, z);
    }

    private static void transformFirstPersonItem(float equipProgress, float swingProgress) {
        GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
        GlStateManager.translate(0.0F, equipProgress * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float curve = MathHelper.sin(swingProgress * swingProgress * PI);
        float rootCurve = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * PI);
        GlStateManager.rotate(curve * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rootCurve * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(rootCurve * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.4F, 0.4F, 0.4F);
    }

    private static void blockTransformation() {
        GlStateManager.translate(-0.5F, 0.2F, 0.0F);
        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
    }

    private static void scale(double value) {
        GlStateManager.scale(value, value, value);
    }
}
