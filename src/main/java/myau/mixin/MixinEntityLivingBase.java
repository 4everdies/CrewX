package myau.mixin;

import myau.Myau;
import myau.event.EventManager;
import myau.events.StrafeEvent;
import myau.management.RotationState;
import myau.module.modules.Animations;
import myau.module.modules.Jesus;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityLivingBase.class}, priority = 9999)
public abstract class MixinEntityLivingBase extends MixinEntity {
    @Inject(method = "getArmSwingAnimationEnd", at = @At("RETURN"), cancellable = true)
    private void crewx$adjustSwingDuration(CallbackInfoReturnable<Integer> callbackInfo) {
        if ((Entity) (Object) this instanceof EntityPlayerSP && Myau.moduleManager != null) {
            Animations animations = (Animations) Myau.moduleManager.modules.get(Animations.class);
            if (animations != null && animations.isEnabled()) {
                callbackInfo.setReturnValue(animations.adjustSwingDuration(callbackInfo.getReturnValue()));
            }
        }
    }

    @ModifyVariable(
            method = {"jump"},
            at = @At("STORE"),
            ordinal = 0
    )
    private float jump(float float1) {
        return (Entity) ((Object) this) instanceof EntityPlayerSP && RotationState.isActived()
                ? RotationState.getSmoothedYaw() * (float) (Math.PI / 180.0)
                : float1;
    }

    @Redirect(
            method = {"moveEntityWithHeading"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;moveFlying(FFF)V"
            )
    )
    private void moveEntityWithHeading(EntityLivingBase entityLivingBase, float float2, float float3, float float4) {
        if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
            StrafeEvent event = new StrafeEvent(float2, float3, float4);
            EventManager.call(event);
            float2 = event.getStrafe();
            float3 = event.getForward();
            float4 = event.getFriction();
            boolean actived = RotationState.isActived();
            float yaw = this.rotationYaw;
            if (actived) {
                this.rotationYaw = RotationState.getSmoothedYaw();
            }
            entityLivingBase.moveFlying(float2, float3, float4);
            if (actived) {
                this.rotationYaw = yaw;
            }
        } else {
            entityLivingBase.moveFlying(float2, float3, float4);
        }
    }

    @ModifyVariable(
            method = {"moveEntityWithHeading"},
            name = {"f3"},
            at = @At("STORE")
    )
    private float moveEntityWithHeading(float float1) {
        if ((EntityLivingBase) ((Object) this) instanceof EntityPlayerSP && float1 == (float) EnchantmentHelper.getDepthStriderModifier((EntityLivingBase) ((Object) this))) {
            if (Myau.moduleManager == null) {
                return float1;
            }
            Jesus jesus = (Jesus) Myau.moduleManager.modules.get(Jesus.class);
            if (jesus.isEnabled() && (!jesus.groundOnly.getValue() || this.onGround)) {
                return Math.max(float1, jesus.speed.getValue());
            }
        }
        return float1;
    }
}
