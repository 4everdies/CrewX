package myau.mixin;

import myau.Myau;
import myau.module.modules.Chams;
import myau.module.modules.ViewClip;
import myau.module.modules.Xray;
import net.minecraft.client.renderer.chunk.SetVisibility;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {VisGraph.class}, priority = 9999)
public abstract class MixinVisGraph {

    @Unique
    private static boolean shouldOverrideVisibility() {
        if (Myau.moduleManager == null) return false;
        return Myau.moduleManager.modules.get(Chams.class).isEnabled()
                || Myau.moduleManager.modules.get(ViewClip.class).isEnabled()
                || Myau.moduleManager.modules.get(Xray.class).isEnabled();
    }

    @Inject(
            method = {"func_178606_a"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void func_178606_a(CallbackInfo callbackInfo) {
        if (shouldOverrideVisibility()) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = {"computeVisibility"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void computeVisibility(CallbackInfoReturnable<SetVisibility> callbackInfoReturnable) {
        if (shouldOverrideVisibility()) {
            SetVisibility setVisibility = new SetVisibility();
            setVisibility.setAllVisible(true);
            callbackInfoReturnable.setReturnValue(setVisibility);
        }
    }
}