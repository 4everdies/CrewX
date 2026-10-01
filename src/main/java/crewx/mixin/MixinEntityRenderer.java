package crewx.mixin;

import crewx.CrewX;
import crewx.data.Box;
import crewx.event.EventManager;
import crewx.events.PickEvent;
import crewx.events.RaytraceEvent;
import crewx.events.Render3DEvent;
import crewx.gui.CrewXTransition;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityRenderer.class}, priority = 9999)
public abstract class MixinEntityRenderer {
    @Unique
    private Box<Integer> slot = null;
    @Unique
    private Box<ItemStack> using = null;
    @Unique
    private Box<Integer> useCount = null;
    @Unique
    private boolean crewx$freeLookRotationApplied;
    @Unique
    private EntityPlayerSP crewx$freeLookPlayer;
    @Unique
    private float crewx$oldRotationYaw;
    @Unique
    private float crewx$oldPrevRotationYaw;
    @Unique
    private float crewx$oldRotationPitch;
    @Unique
    private float crewx$oldPrevRotationPitch;
    @Shadow
    private Minecraft mc;
    @Shadow
    private float thirdPersonDistance;

    @Inject(
            method = "updateCameraAndRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V", shift = At.Shift.BEFORE)
    )
    private void crewx$beginGuiTransition(float partialTicks, long finishTimeNano, CallbackInfo callbackInfo) {
        if (this.mc.currentScreen instanceof GuiMainMenu) {
            CrewXTransition.beginGuiRender();
        }
    }

    @Inject(
            method = "updateCameraAndRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V", shift = At.Shift.AFTER)
    )
    private void crewx$endGuiTransition(float partialTicks, long finishTimeNano, CallbackInfo callbackInfo) {
        if (this.mc.currentScreen instanceof GuiMainMenu) {
            CrewXTransition.endGuiRender();
        }
    }

    @Inject(
            method = {"updateCameraAndRender"},
            at = {@At("HEAD")}
    )
    private void updateCameraAndRender(float float1, long long2, CallbackInfo callbackInfo) {
        if (this.mc.thePlayer != null && CrewX.moduleManager != null) {
            FreeLook freeLook = (FreeLook) CrewX.moduleManager.modules.get(FreeLook.class);
            if (freeLook != null && freeLook.isEnabled()) freeLook.prepare(this.mc.thePlayer);
            Scaffold scaffold = (Scaffold) CrewX.moduleManager.modules.get(Scaffold.class);
            if (scaffold.isEnabled() && scaffold.itemSpoof.getValue()) {
                int slot = scaffold.getSlot();
                if (slot >= 0) {
                    this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);
                    this.mc.thePlayer.inventory.currentItem = slot;
                }
            }
            KillAura killAura = (KillAura) CrewX.moduleManager.modules.get(KillAura.class);
            if (killAura.isEnabled() && killAura.isBlocking()) {
                this.using = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUse());
                ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUse(this.mc.thePlayer.inventory.getCurrentItem());
                this.useCount = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUseCount());
                ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(69000);
            }
        }
    }

    @Inject(
            method = {"updateCameraAndRender"},
            at = {@At("RETURN")}
    )
    private void postUpdateCameraAndRender(float float1, long long2, CallbackInfo callbackInfo) {
        if (this.slot != null) {
            this.mc.thePlayer.inventory.currentItem = this.slot.value;
            this.slot = null;
        }
        if (this.using != null) {
            ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUse(this.using.value);
            this.using = null;
        }
                if (this.useCount != null) {
            ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(this.useCount.value);
            this.useCount = null;
        }
    }

    @Redirect(
            method = "updateCameraAndRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;setAngles(FF)V", ordinal = 0)
    )
    private void crewx$freeLookMouseSmooth(EntityPlayerSP player, float yawDelta, float pitchDelta) {
        this.crewx$applyFreeLookMouse(player, yawDelta, pitchDelta);
    }

    @Redirect(
            method = "updateCameraAndRender",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;setAngles(FF)V", ordinal = 1)
    )
    private void crewx$freeLookMouse(EntityPlayerSP player, float yawDelta, float pitchDelta) {
        this.crewx$applyFreeLookMouse(player, yawDelta, pitchDelta);
    }

    @Unique
    private void crewx$applyFreeLookMouse(EntityPlayerSP player, float yawDelta, float pitchDelta) {
        if (CrewX.moduleManager != null) {
            FreeLook freeLook = (FreeLook) CrewX.moduleManager.modules.get(FreeLook.class);
            if (freeLook != null && freeLook.isEnabled() && this.mc.gameSettings.thirdPersonView != 0) {
                freeLook.applyMouseDelta(player, yawDelta, pitchDelta);
                return;
            }
        }
        player.setAngles(yawDelta, pitchDelta);
    }

    @Inject(method = "orientCamera", at = @At("HEAD"))
    private void crewx$applyFreeLookCamera(float partialTicks, CallbackInfo callbackInfo) {
        if (this.crewx$freeLookRotationApplied || this.mc.thePlayer == null || CrewX.moduleManager == null
                || this.mc.gameSettings.thirdPersonView == 0) return;

        FreeLook freeLook = (FreeLook) CrewX.moduleManager.modules.get(FreeLook.class);
        if (freeLook == null || !freeLook.isEnabled()) return;

        EntityPlayerSP player = this.mc.thePlayer;
        freeLook.prepare(player);
        this.crewx$freeLookPlayer = player;
        this.crewx$oldRotationYaw = player.rotationYaw;
        this.crewx$oldPrevRotationYaw = player.prevRotationYaw;
        this.crewx$oldRotationPitch = player.rotationPitch;
        this.crewx$oldPrevRotationPitch = player.prevRotationPitch;
        player.rotationYaw = freeLook.getCameraYaw();
        player.prevRotationYaw = freeLook.getCameraYaw();
        player.rotationPitch = freeLook.getCameraPitch();
        player.prevRotationPitch = freeLook.getCameraPitch();
        this.crewx$freeLookRotationApplied = true;
    }

    @Inject(method = "orientCamera", at = @At("RETURN"))
    private void crewx$restoreFreeLookPlayer(float partialTicks, CallbackInfo callbackInfo) {
        if (!this.crewx$freeLookRotationApplied || this.crewx$freeLookPlayer == null) return;
        this.crewx$freeLookPlayer.rotationYaw = this.crewx$oldRotationYaw;
        this.crewx$freeLookPlayer.prevRotationYaw = this.crewx$oldPrevRotationYaw;
        this.crewx$freeLookPlayer.rotationPitch = this.crewx$oldRotationPitch;
        this.crewx$freeLookPlayer.prevRotationPitch = this.crewx$oldPrevRotationPitch;
        this.crewx$freeLookPlayer = null;
        this.crewx$freeLookRotationApplied = false;
    }

    @Inject(

            method = {"updateRenderer"},
            at = {@At("HEAD")}
    )
    private void updateRenderer(CallbackInfo callbackInfo) {
        Scaffold scaffold = (Scaffold) CrewX.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled() && scaffold.itemSpoof.getValue()) {
            int slot = scaffold.getSlot();
            if (slot >= 0) {
                this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);
                this.mc.thePlayer.inventory.currentItem = slot;
            }
        }

        AutoBlockIn autoBlockIn = (AutoBlockIn) CrewX.moduleManager.modules.get(AutoBlockIn.class);
        if (autoBlockIn.isEnabled() && autoBlockIn.itemSpoof.getValue()) {
            int slot = autoBlockIn.getSlot();
            if (slot >= 0) {
                this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);
                this.mc.thePlayer.inventory.currentItem = slot;
            }
        }
    }

    @Inject(
            method = {"updateRenderer"},
            at = {@At("RETURN")}
    )
    private void postUpdateRenderer(CallbackInfo callbackInfo) {
        if (this.slot != null) {
            this.mc.thePlayer.inventory.currentItem = this.slot.value;
            this.slot = null;
        }
    }

    @Inject(
            method = {"renderWorldPass"},
            at = {@At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z",
                    shift = At.Shift.BEFORE
            )}
    )
    private void renderWorldPass(int integer, float float2, long long3, CallbackInfo callbackInfo) {
        crewx.util.RenderUtil.updateFrustum();
        EventManager.call(new Render3DEvent(float2));
    }

    @ModifyConstant(
            method = {"hurtCameraEffect"},
            constant = {@Constant(
                    floatValue = 14.0F,
                    ordinal = 0
            )}
    )
    private float hurtCameraEffect(float float1) {
        if (CrewX.moduleManager == null) {
            return float1;
        } else {
            NoHurtCam noHurtCam = (NoHurtCam) CrewX.moduleManager.modules.get(NoHurtCam.class);
            return noHurtCam.isEnabled() ? float1 * (float) noHurtCam.multiplier.getValue().intValue() / 100.0F : float1;
        }
    }

    @ModifyConstant(
            method = {"getMouseOver"},
            constant = {@Constant(
                    doubleValue = 3.0,
                    ordinal = 1
            )}
    )
    private double getMouseOver(double range) {
        PickEvent event = new PickEvent(range);
        EventManager.call(event);
        return event.getRange();
    }

    @ModifyVariable(
            method = {"getMouseOver"},
            at = @At("STORE"),
            name = {"d0"}
    )
    private double storeMouseOver(double range) {
        RaytraceEvent event = new RaytraceEvent(range);
        EventManager.call(event);
        return event.getRange();
    }

    @Inject(
            method = {"getMouseOver"},
            at = {@At(
                    value = "INVOKE",
                    target = "Ljava/util/List;size()I",
                    ordinal = 0
            )},
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void a(
            float float1,
            CallbackInfo callbackInfo,
            Entity entity,
            double double4,
            double double5,
            Vec3 vec36,
            boolean boolean7,
            int integer8,
            Vec3 vec39,
            Vec3 vec310,
            Vec3 vec311,
            float float12,
            List<Entity> list,
            double double14,
            int integer15
    ) {
        if (CrewX.moduleManager != null) {
            GhostHand event = (GhostHand) CrewX.moduleManager.modules.get(GhostHand.class);
            if (event.isEnabled()) {
                list.removeIf(event::shouldSkip);
            }
        }
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Vec3;distanceTo(Lnet/minecraft/util/Vec3;)D"
            )
    )
    private double v(Vec3 vec31, Vec3 vec32) {
        if (CrewX.moduleManager == null) {
            return vec31.distanceTo(vec32);
        } else {
            return CrewX.moduleManager.modules.get(ViewClip.class).isEnabled() ? (double) this.thirdPersonDistance : vec31.distanceTo(vec32);
        }
    }

    @Redirect(
            method = {"setupFog"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;getMaterial()Lnet/minecraft/block/material/Material;"
            )
    )
    private Material x(Block block) {
        if (CrewX.moduleManager == null) {
            return block.getMaterial();
        } else {
            return CrewX.moduleManager.modules.get(ViewClip.class).isEnabled() ? Material.air : block.getMaterial();
        }
    }

    @Redirect(
            method = {"updateFogColor"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean y(EntityLivingBase entityLivingBase, Potion potion) {
        if (potion == Potion.blindness && CrewX.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) CrewX.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff.isEnabled() && antiDebuff.blindness.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityLivingBase).getActivePotionsMap().containsKey(potion.id);
    }

    @Redirect(
            method = {"setupFog"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean q(EntityLivingBase entityLivingBase, Potion potion) {
        if (potion == Potion.blindness && CrewX.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) CrewX.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff.isEnabled() && antiDebuff.blindness.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityLivingBase).getActivePotionsMap().containsKey(potion.id);
    }

    @Redirect(
            method = {"setupCameraTransform"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean c(EntityPlayerSP entityPlayerSP, Potion potion) {
        if (potion == Potion.confusion && CrewX.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) CrewX.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff.isEnabled() && antiDebuff.nausea.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityPlayerSP).getActivePotionsMap().containsKey(potion.id);
    }
}
