package crewx.module.modules.render;
import crewx.module.modules.combat.*;
import crewx.module.modules.movement.*;
import crewx.module.modules.render.*;
import crewx.module.modules.player.*;
import crewx.module.modules.misc.*;

import crewx.CrewX;
import crewx.event.EventTarget;
import crewx.events.Render3DEvent;
import crewx.mixin.IAccessorMinecraft;
import crewx.mixin.IAccessorRenderManager;
import crewx.module.Module;
import crewx.property.properties.BooleanProperty;
import crewx.property.properties.ColorProperty;
import crewx.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.stream.Collectors;

public class ChestESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ColorProperty chest = new ColorProperty("chest", new Color(255, 170, 0).getRGB());
    public final ColorProperty trappedChest = new ColorProperty("trapped-chest", new Color(255, 43, 0).getRGB());
    public final ColorProperty enderChest = new ColorProperty("ender-chest", new Color(26, 17, 0).getRGB());
    public final BooleanProperty tracers = new BooleanProperty("tracers", false);

    public ChestESP() {
        super("ChestESP", false);
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled()) {
            RenderUtil.enableRenderState();
            for (TileEntity chest : mc.theWorld.loadedTileEntityList.stream().filter(tileEntity -> tileEntity instanceof TileEntityChest || tileEntity instanceof TileEntityEnderChest).collect(Collectors.toList())) {
                Block block = mc.theWorld.getBlockState(chest.getPos()).getBlock();
                double minX, minZ, maxX, maxZ;
                Color color;
                minX = minZ = 0.0625;
                maxX = maxZ = 0.9375;
                if (block instanceof BlockChest) {
                    if (block.canProvidePower()) {
                        color = new Color(this.trappedChest.getValue());
                    } else {
                        color = new Color(this.chest.getValue());
                    }
                    EnumFacing facing = mc.theWorld.getBlockState(chest.getPos()).getValue(BlockChest.FACING);
                    switch (facing) {
                        case NORTH:
                            if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) {
                                minX -= 1;
                            }
                            break;
                        case SOUTH:
                            if (mc.theWorld.getBlockState(chest.getPos().west()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().east()).getBlock() == block) {
                                maxX += 1;
                            }
                            break;
                        case WEST:
                            if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) {
                                maxZ += 1;
                            }
                            break;
                        case EAST:
                            if (mc.theWorld.getBlockState(chest.getPos().south()).getBlock() == block) {
                                continue;
                            } else if (mc.theWorld.getBlockState(chest.getPos().north()).getBlock() == block) {
                                minZ -= 1;
                            }
                            break;
                        default:
                            continue;
                    }
                } else {
                    color = new Color(this.enderChest.getValue());
                }
                AxisAlignedBB aabb = new AxisAlignedBB(
                        (double) chest.getPos().getX() + minX,
                        (double) chest.getPos().getY() + 0.0,
                        (double) chest.getPos().getZ() + minZ,
                        (double) chest.getPos().getX() + maxX,
                        (double) chest.getPos().getY() + 0.875,
                        (double) chest.getPos().getZ() + maxZ
                )
                        .offset(
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                        );
                RenderUtil.drawBoundingBox(
                        aabb, color.getRed(), color.getGreen(), color.getBlue(), 255, 1.5F
                );
                if (this.tracers.getValue()) {
                    Vec3 vec;
                    if (mc.gameSettings.thirdPersonView == 0) {
                        vec = new Vec3(0.0, 0.0, 1.0)
                                .rotatePitch(
                                        (float) (
                                                -Math.toRadians(
                                                        RenderUtil.lerpFloat(
                                                                mc.getRenderViewEntity().rotationPitch,
                                                                mc.getRenderViewEntity().prevRotationPitch,
                                                                crewx.util.WorldRenderPass.partialTicks()
                                                        )
                                                )
                                        )
                                )
                                .rotateYaw(
                                        (float) (
                                                -Math.toRadians(
                                                        RenderUtil.lerpFloat(
                                                                mc.getRenderViewEntity().rotationYaw,
                                                                mc.getRenderViewEntity().prevRotationYaw,
                                                                crewx.util.WorldRenderPass.partialTicks()
                                                        )
                                                )
                                        )
                                );
                    } else {
                        vec = new Vec3(0.0, 0.0, 0.0)
                                .rotatePitch(
                                        (float) (
                                                -Math.toRadians(
                                                        RenderUtil.lerpFloat(
                                                                mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, crewx.util.WorldRenderPass.partialTicks()
                                                        )
                                                )
                                        )
                                )
                                .rotateYaw(
                                        (float) (
                                                -Math.toRadians(
                                                        RenderUtil.lerpFloat(
                                                                mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, crewx.util.WorldRenderPass.partialTicks()
                                                        )
                                                )
                                        )
                                );
                    }
                    vec = new Vec3(vec.xCoord, vec.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), vec.zCoord);
                    float opacity = (float) ((Tracers) CrewX.moduleManager.modules.get(Tracers.class)).opacity.getValue() / 100.0F;
                    RenderUtil.drawLine3D(
                            vec,
                            (double) chest.getPos().getX() + 0.5,
                            (double) chest.getPos().getY() + 0.5,
                            (double) chest.getPos().getZ() + 0.5,
                            (float) color.getRed() / 255.0F,
                            (float) color.getGreen() / 255.0F,
                            (float) color.getBlue() / 255.0F,
                            opacity,
                            1.5F
                    );
                }
            }
            RenderUtil.disableRenderState();
        }
    }
}