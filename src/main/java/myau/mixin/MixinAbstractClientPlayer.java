package myau.mixin;

import myau.Myau;
import myau.module.modules.Sprint;
import myau.script.ScriptSkinOverrides;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {AbstractClientPlayer.class}, priority = 9999)
public abstract class MixinAbstractClientPlayer extends MixinEntityPlayer {
    @Inject(method = {"getLocationSkin"}, at = @At("HEAD"), cancellable = true)
    private void crewx$getLocationSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation override = ScriptSkinOverrides.getTexture((AbstractClientPlayer) (Object) this);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }

    @Inject(method = {"getSkinType"}, at = @At("HEAD"), cancellable = true)
    private void crewx$getSkinType(CallbackInfoReturnable<String> cir) {
        String model = ScriptSkinOverrides.getModel((AbstractClientPlayer) (Object) this);
        if (model != null) {
            cir.setReturnValue(model);
        }
    }

    @Redirect(
            method = {"getFovModifier"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/attributes/IAttributeInstance;getAttributeValue()D"
            )
    )
    private double getFovModifier(IAttributeInstance iAttributeInstance) {
        double attributeValue = iAttributeInstance.getAttributeValue();
        if ((((Entity) (Object) this)) instanceof EntityPlayerSP && Myau.moduleManager != null) {
            Sprint sprint = (Sprint) Myau.moduleManager.modules.get(Sprint.class);
            return sprint.isEnabled() && sprint.shouldApplyFovFix(iAttributeInstance) ? attributeValue * 1.300000011920929 : attributeValue;
        } else {
            return attributeValue;
        }
    }
}