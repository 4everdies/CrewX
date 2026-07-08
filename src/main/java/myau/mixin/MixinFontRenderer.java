package myau.mixin;

import myau.Myau;
import myau.module.modules.AntiObfuscate;
import myau.module.modules.NickHider;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@SideOnly(Side.CLIENT)
@Mixin(value = {FontRenderer.class}, priority = 9999)
public abstract class MixinFontRenderer {

    @Unique
    private static String processString(String string) {
        if (Myau.moduleManager == null) return string;
        AntiObfuscate antiObfuscate = (AntiObfuscate) Myau.moduleManager.modules.get(AntiObfuscate.class);
        if (antiObfuscate.isEnabled()) {
            string = antiObfuscate.stripObfuscated(string);
        }
        NickHider nickHider = (NickHider) Myau.moduleManager.modules.get(NickHider.class);
        return nickHider.isEnabled() ? nickHider.replaceNick(string) : string;
    }

    @ModifyVariable(
            method = {"renderString"},
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private String renderString(String string) {
        return processString(string);
    }

    @ModifyVariable(
            method = {"getStringWidth"},
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private String getStringWidth(String string) {
        return processString(string);
    }

    @Redirect(
            method = {"getStringWidth"},
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;charAt(I)C",
                    ordinal = 1
            )
    )
    private char getStringWidth(String string, int index) {
        char c = string.charAt(index);

        return "0123456789aAbBcCdDeEfF".indexOf(c) >= 0 ? 'r' : c;
    }
}