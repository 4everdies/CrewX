package myau.mixin;

import myau.Myau;
import myau.module.modules.Animations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = ItemRenderer.class, priority = 9999)
public abstract class MixinItemRenderer {
    @Shadow
    private Minecraft mc;
    @Shadow
    private ItemStack itemToRender;
    @Shadow
    private float equippedProgress;
    @Shadow
    private float prevEquippedProgress;

    @Shadow
    private void rotateArroundXAndY(float angle, float angleY) {
        throw new AssertionError();
    }

    @Shadow
    private void setLightMapFromPlayer(AbstractClientPlayer player) {
        throw new AssertionError();
    }

    @Shadow
    private void rotateWithPlayerRotations(EntityPlayerSP player, float partialTicks) {
        throw new AssertionError();
    }

    @Shadow
    public abstract void renderItem(EntityLivingBase entity, ItemStack stack,
                                    ItemCameraTransforms.TransformType transformType);

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
    private void crewx$renderAnimations(float partialTicks, CallbackInfo callbackInfo) {
        Animations animations = getAnimations();
        EntityPlayerSP player = this.mc.thePlayer;
        if (animations == null || !animations.isEnabled() || player == null || this.itemToRender == null
                || this.itemToRender.getItem() instanceof ItemMap) {
            return;
        }

        boolean using = player.getItemInUseCount() > 0;
        boolean blocking = using && this.itemToRender.getItemUseAction() == EnumAction.BLOCK;
        // The source module only cancels vanilla rendering while blocking or swinging.
        if (using && !blocking) {
            return;
        }

        float equip = 1.0F - (this.prevEquippedProgress
                + (this.equippedProgress - this.prevEquippedProgress) * partialTicks);
        float swing = player.getSwingProgress(partialTicks);
        float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
        float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;

        this.rotateArroundXAndY(pitch, yaw);
        this.setLightMapFromPlayer(player);
        this.rotateWithPlayerRotations(player, partialTicks);
        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();
        try {
            animations.applyFirstPersonTransform(blocking, equip, swing, this.equippedProgress, player.isSneaking());
            this.renderItem(player, this.itemToRender, ItemCameraTransforms.TransformType.FIRST_PERSON);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
        }
        callbackInfo.cancel();
    }

    /** Adds X/Y/Z to vanilla eat, drink, bow and NONE-use transformations. */
    @Inject(method = "transformFirstPersonItem", at = @At("HEAD"))
    private void crewx$translateVanillaUse(float equipProgress, float swingProgress, CallbackInfo callbackInfo) {
        Animations animations = getAnimations();
        if (animations != null && animations.isEnabled() && !animations.onlyWhenBlocking.getValue()) {
            animations.translatePosition();
        }
    }

    private static Animations getAnimations() {
        if (Myau.moduleManager == null) {
            return null;
        }
        return (Animations) Myau.moduleManager.modules.get(Animations.class);
    }
}
