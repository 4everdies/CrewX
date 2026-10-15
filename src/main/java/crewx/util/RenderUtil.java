package crewx.util;


import crewx.gui.ClientFont;
import crewx.enums.ChatColors;
import crewx.mixin.IAccessorEntityRenderer;
import crewx.mixin.IAccessorMinecraft;
import crewx.mixin.IAccessorRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import javax.vecmath.Vector3d;
import javax.vecmath.Vector4d;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public class RenderUtil {
    private static Minecraft mc;
    private static Frustum cameraFrustum;
    private static IntBuffer viewportBuffer;
    private static FloatBuffer modelViewBuffer;
    private static FloatBuffer projectionBuffer;
    private static FloatBuffer vectorBuffer;
    private static Map<Integer, EnchantmentData> enchantmentMap;

    static {
        RenderUtil.mc = Minecraft.getMinecraft();
        RenderUtil.cameraFrustum = new Frustum();
        RenderUtil.viewportBuffer = GLAllocation.createDirectIntBuffer(16);
        RenderUtil.modelViewBuffer = GLAllocation.createDirectFloatBuffer(16);
        RenderUtil.projectionBuffer = GLAllocation.createDirectFloatBuffer(16);
        RenderUtil.vectorBuffer = GLAllocation.createDirectFloatBuffer(4);
        RenderUtil.enchantmentMap = new EnchantmentMap();
    }

    private static ChatColors getColorForLevel(int currentLevel, int maxLevel) {
        if (currentLevel > maxLevel) {
            return ChatColors.LIGHT_PURPLE;
        }
        if (currentLevel == maxLevel) {
            return ChatColors.RED;
        }
        switch (currentLevel) {
            case 1: {
                return ChatColors.AQUA;
            }
            case 2: {
                return ChatColors.GREEN;
            }
            case 3: {
                return ChatColors.YELLOW;
            }
            case 4: {
                return ChatColors.GOLD;
            }
        }
        return ChatColors.GRAY;
    }

    public static void drawOutlinedString(String text, float x, float y) {
        String string2 = text.replaceAll("(?i)§[\\da-f]", "");
        ClientFont.drawString(string2, x + 1.0f, y, 0, false);
        ClientFont.drawString(string2, x - 1.0f, y, 0, false);
        ClientFont.drawString(string2, x, y + 1.0f, 0, false);
        ClientFont.drawString(string2, x, y - 1.0f, 0, false);
        ClientFont.drawString(text, x, y, -1, false);
    }

    public static void renderEnchantmentText(ItemStack itemStack, float x, float y, float scale) {
        NBTTagList nBTTagList;
        nBTTagList = itemStack.getItem() == Items.enchanted_book ? Items.enchanted_book.getEnchantments(itemStack) : itemStack.getEnchantmentTagList();
        if (nBTTagList != null) {
            for (int i = 0; i < nBTTagList.tagCount(); ++i) {
                EnchantmentData enchantmentData = enchantmentMap.get(nBTTagList.getCompoundTagAt(i).getInteger("id"));
                if (enchantmentData == null) {
                    continue;
                }
                short s = nBTTagList.getCompoundTagAt(i).getShort("lvl");
                ChatColors chatColors = RenderUtil.getColorForLevel(s, enchantmentData.maxLevel);
                RenderUtil.drawOutlinedString(ChatColors.formatColor(String.format("&r%s%s%d&r", enchantmentData.shortName, chatColors, (int) s)), x * (1.0f / scale), (y + (float) i * 4.0f) * (1.0f / scale));
            }
        }
    }

    public static void renderItemInGUI(ItemStack itemStack, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.clear(256);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f, -0.01f);
        RenderUtil.mc.getRenderItem().zLevel = -150.0f;
        mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, x, y);
        mc.getRenderItem().renderItemOverlays(RenderUtil.mc.fontRendererObj, itemStack, x, y);
        RenderUtil.mc.getRenderItem().zLevel = 0.0f;
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
        GlStateManager.disableDepth();
        RenderUtil.renderEnchantmentText(itemStack, x, y, 0.5f);
        GlStateManager.enableDepth();
        GlStateManager.scale(2.0f, 2.0f, 2.0f);
        GlStateManager.popMatrix();
    }

    public static void renderPotionEffect(PotionEffect potionEffect, int x, int y) {
        int n3 = Potion.potionTypes[potionEffect.getPotionID()].getStatusIconIndex();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.clear(256);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f, -0.01f);
        mc.getTextureManager().bindTexture(new ResourceLocation("textures/gui/container/inventory.png"));
        Gui.drawModalRectWithCustomSizedTexture(x, y, n3 % 8 * 18, 198 + n3 / 8 * 18, 18, 18, 256.0f, 256.0f);
        GlStateManager.popMatrix();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public static void drawRect(float x1, float y1, float x2, float y2, int color) {
        if (color == 0) {
            return;
        }
        RenderUtil.setColor(color);
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    public static void drawRect3D(float x1, float y1, float x2, float y2, int color) {
        if (color == 0) {
            return;
        }
        RenderUtil.setColor(color);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_POLYGON);
        for (int i = 0; i < 2; ++i) {
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x1, y2);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x2, y1);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.resetColor();
    }

    public static void drawOutlineRect(float x1, float y1, float x2, float y2, float lineWidth, int backgroundColor, int lineColor) {
        RenderUtil.drawRect(0.0f, 0.0f, x2, 27.0f, backgroundColor);
        if (lineColor == 0) {
            return;
        }
        RenderUtil.setColor(lineColor);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawLine(float x1, float y1, float x2, float y2, float lineWidth, int color) {
        RenderUtil.setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawLine3D(Vec3 start, double endX, double endY, double endZ, float red, float green, float blue, float alpha, float lineWidth) {
        GlStateManager.pushMatrix();
        GlStateManager.color(red, green, blue, alpha);
        boolean bl = RenderUtil.mc.gameSettings.viewBobbing;
        RenderUtil.mc.gameSettings.viewBobbing = false;
        ((IAccessorEntityRenderer) RenderUtil.mc.entityRenderer).callSetupCameraTransform(WorldRenderPass.partialTicks(), 2);
        RenderUtil.mc.gameSettings.viewBobbing = bl;
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(start.xCoord, start.yCoord, start.zCoord);
        GL11.glVertex3d(endX - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), endY - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), endZ - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    public static void drawArrow(float centerX, float centerY, float angle, float length, float lineWidth, int color) {
        float f6 = angle + (float) Math.toRadians(45.0);
        float f7 = angle - (float) Math.toRadians(45.0);
        RenderUtil.setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX + length * (float) Math.cos(f6), centerY + length * (float) Math.sin(f6));
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX + length * (float) Math.cos(f7), centerY + length * (float) Math.sin(f7));
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawTriangle(float centerX, float centerY, float angle, float length, int color) {
        float f5 = angle + (float) Math.toRadians(26.25);
        float f6 = angle - (float) Math.toRadians(26.25);
        RenderUtil.setColor(color);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(9);
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX + length * (float) Math.cos(f5), centerY + length * (float) Math.sin(f5));
        GL11.glVertex2f(centerX + length * (float) Math.cos(f6), centerY + length * (float) Math.sin(f6));
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.resetColor();
    }

    public static void drawFramebuffer(Framebuffer framebuffer) {
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        GlStateManager.bindTexture(framebuffer.framebufferTexture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(0.0, 1.0);
        GL11.glVertex2d(0.0, 0.0);
        GL11.glTexCoord2d(0.0, 0.0);
        GL11.glVertex2d(0.0, scaledResolution.getScaledHeight());
        GL11.glTexCoord2d(1.0, 0.0);
        GL11.glVertex2d(scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight());
        GL11.glTexCoord2d(1.0, 1.0);
        GL11.glVertex2d(scaledResolution.getScaledWidth(), 0.0);
        GL11.glEnd();
    }

    public static void fillCircle(double x, double y, double radius, int segments, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        RenderUtil.setColor(color);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);

        GL11.glVertex2d(x, y);

        for (int i = 0; i <= segments; i++) {
            double angle = i * (Math.PI * 2.0 / segments);
            double px = x + Math.cos(angle) * radius;
            double py = y + Math.sin(angle) * radius;
            GL11.glVertex2d(px, py);
        }

        GL11.glEnd();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }

    public static void drawCircle(double centerX, double centerY, double centerZ, double radius, int segments, int color) {
        RenderUtil.setColor(color);
        GL11.glLineWidth(3.0f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= segments; ++i) {
            double d5 = (double) i * (Math.PI * 2 / (double) segments);
            GL11.glVertex3d(centerX + Math.cos(d5) * radius, centerY, centerZ + Math.sin(d5) * radius);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawEntityCircle(Entity entity, double radius, int segments, int color) {
        double d2 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, WorldRenderPass.partialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double d3 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, WorldRenderPass.partialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double d4 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, WorldRenderPass.partialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        RenderUtil.drawCircle(d2, d3, d4, radius, segments, color);
    }

    public static void drawFilledBox(AxisAlignedBB axisAlignedBB, int red, int green, int blue) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        tessellator.draw();
    }

    public static void drawBoundingBox(AxisAlignedBB axisAlignedBB, int red, int green, int blue, int alpha, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        RenderGlobal.drawOutlinedBoundingBox(axisAlignedBB, red, green, blue, alpha);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
    }

    /**
     * A softer box than the flat fill + hard outline: faces with a vertical gradient (denser at the feet, clear at
     * the top), a faint wide glow around the edges and a crisp outline on top. Expects the render state of
     * {@link #enableRenderState()} (blend on, texture/depth/cull off).
     */
    public static void drawStyledBox(AxisAlignedBB bb, int rgb, float fillBottom, float fillTop, float lineWidth) {
        int r = rgb >> 16 & 255;
        int g = rgb >> 8 & 255;
        int b = rgb & 255;
        int bottom = Math.round(255.0F * fillBottom);
        int top = Math.round(255.0F * fillTop);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        // sides (gradient), then the bottom and top faces
        sideQuad(wr, bb.minX, bb.minZ, bb.maxX, bb.minZ, bb.minY, bb.maxY, r, g, b, bottom, top);
        sideQuad(wr, bb.maxX, bb.minZ, bb.maxX, bb.maxZ, bb.minY, bb.maxY, r, g, b, bottom, top);
        sideQuad(wr, bb.maxX, bb.maxZ, bb.minX, bb.maxZ, bb.minY, bb.maxY, r, g, b, bottom, top);
        sideQuad(wr, bb.minX, bb.maxZ, bb.minX, bb.minZ, bb.minY, bb.maxY, r, g, b, bottom, top);
        wr.pos(bb.minX, bb.minY, bb.minZ).color(r, g, b, bottom).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.minZ).color(r, g, b, bottom).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.maxZ).color(r, g, b, bottom).endVertex();
        wr.pos(bb.minX, bb.minY, bb.maxZ).color(r, g, b, bottom).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.minZ).color(r, g, b, top).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.maxZ).color(r, g, b, top).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.maxZ).color(r, g, b, top).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.minZ).color(r, g, b, top).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        // glow, then the crisp edge
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(lineWidth + 3.0F);
        RenderGlobal.drawOutlinedBoundingBox(bb, r, g, b, 45);
        GL11.glLineWidth(lineWidth);
        RenderGlobal.drawOutlinedBoundingBox(bb, Math.min(255, r + 25), Math.min(255, g + 25), Math.min(255, b + 25), 235);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0F);
        GlStateManager.depthMask(true);
    }

    private static void sideQuad(WorldRenderer wr, double x1, double z1, double x2, double z2, double minY, double maxY,
                                 int r, int g, int b, int bottom, int top) {
        wr.pos(x1, minY, z1).color(r, g, b, bottom).endVertex();
        wr.pos(x1, maxY, z1).color(r, g, b, top).endVertex();
        wr.pos(x2, maxY, z2).color(r, g, b, top).endVertex();
        wr.pos(x2, minY, z2).color(r, g, b, bottom).endVertex();
    }

    /** {@link #drawStyledBox} around an entity at its interpolated position (camera-relative). */
    public static void drawStyledEntityBox(Entity entity, int rgb, double expand, float fillBottom, float fillTop, float lineWidth) {
        float partialTicks = WorldRenderPass.partialTicks();
        double x = lerpDouble(entity.posX, entity.lastTickPosX, partialTicks);
        double y = lerpDouble(entity.posY, entity.lastTickPosY, partialTicks);
        double z = lerpDouble(entity.posZ, entity.lastTickPosZ, partialTicks);
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        drawStyledBox(entity.getEntityBoundingBox().expand(expand, expand, expand)
                .offset(x - entity.posX, y - entity.posY, z - entity.posZ)
                .offset(-renderManager.getRenderPosX(), -renderManager.getRenderPosY(), -renderManager.getRenderPosZ()),
                rgb, fillBottom, fillTop, lineWidth);
    }

    /** Screen-space rectangle with a vertical colour gradient (ARGB). */
    public static void drawGradientRect2D(float x1, float y1, float x2, float y2, int topColor, int bottomColor) {
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y1, 0.0D).color(topColor >> 16 & 255, topColor >> 8 & 255, topColor & 255, topColor >>> 24).endVertex();
        wr.pos(x1, y2, 0.0D).color(bottomColor >> 16 & 255, bottomColor >> 8 & 255, bottomColor & 255, bottomColor >>> 24).endVertex();
        wr.pos(x2, y2, 0.0D).color(bottomColor >> 16 & 255, bottomColor >> 8 & 255, bottomColor & 255, bottomColor >>> 24).endVertex();
        wr.pos(x2, y1, 0.0D).color(topColor >> 16 & 255, topColor >> 8 & 255, topColor & 255, topColor >>> 24).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
    }

    public static void drawEntityBox(Entity entity, int red, int green, int blue) {
        double d2 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, WorldRenderPass.partialTicks());
        double d3 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, WorldRenderPass.partialTicks());
        double d4 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, WorldRenderPass.partialTicks());
        RenderUtil.drawFilledBox(entity.getEntityBoundingBox().expand(0.1f, 0.1f, 0.1f).offset(d2 - entity.posX, d3 - entity.posY, d4 - entity.posZ).offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), red, green, blue);
    }

    public static void drawEntityBoundingBox(Entity entity, int red, int green, int blue, int alpha, float lineWidth, double expand) {
        double d2 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, WorldRenderPass.partialTicks());
        double d3 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, WorldRenderPass.partialTicks());
        double d4 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, WorldRenderPass.partialTicks());
        RenderUtil.drawBoundingBox(entity.getEntityBoundingBox().expand(expand, expand, expand).offset(d2 - entity.posX, d3 - entity.posY, d4 - entity.posZ).offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), red, green, blue, alpha, lineWidth);
    }

    public static void drawBlockBox(BlockPos blockPos, double height, int red, int green, int blue) {
        RenderUtil.drawFilledBox(new AxisAlignedBB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), (double) blockPos.getX() + 1.0, (double) blockPos.getY() + height, (double) blockPos.getZ() + 1.0).offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), red, green, blue);
    }

    public static void drawBlockBoundingBox(BlockPos blockPos, double height, int red, int green, int blue, int alpha, float lineWidth) {
        RenderUtil.drawBoundingBox(new AxisAlignedBB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), (double) blockPos.getX() + 1.0, (double) blockPos.getY() + height, (double) blockPos.getZ() + 1.0).offset(-((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(), -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), red, green, blue, alpha, lineWidth);
    }

    public static void drawCornerESP(EntityPlayer entity, float red, float green, float blue) {
        float x = (float) (RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, WorldRenderPass.partialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX());
        float y = (float) (RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, WorldRenderPass.partialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY());
        float z = (float) (RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, WorldRenderPass.partialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height / 2.0F, z);
        faceCamera();
        GlStateManager.scale(-0.098F, -0.098F, 0.098F);
        float width = (float) (26.6 * entity.width / 2.0);
        float height = 12.0F;
        GlStateManager.color(red, green, blue);
        draw3DRect(width, height - 1.0F, width - 4.0F, height);
        draw3DRect(-width, height - 1.0F, -width + 4.0F, height);
        draw3DRect(-width, height, -width + 1.0F, height - 4.0F);
        draw3DRect(width, height, width - 1.0F, height - 4.0F);
        draw3DRect(width, -height, width - 4.0F, -height + 1.0F);
        draw3DRect(-width, -height, -width + 4.0F, -height + 1.0F);
        draw3DRect(-width, -height + 1.0F, -width + 1.0F, -height + 4.0F);
        draw3DRect(width, -height + 1.0F, width - 1.0F, -height + 4.0F);
        GlStateManager.color(0.0F, 0.0F, 0.0F);
        draw3DRect(width, height, width - 4.0F, height + 0.2F);
        draw3DRect(-width, height, -width + 4.0F, height + 0.2F);
        draw3DRect(-width - 0.2F, height + 0.2F, -width, height - 4.0F);
        draw3DRect(width + 0.2F, height + 0.2F, width, height - 4.0F);
        draw3DRect(width + 0.2F, -height, width - 4.0F, -height - 0.2F);
        draw3DRect(-width - 0.2F, -height, -width + 4.0F, -height - 0.2F);
        draw3DRect(-width - 0.2F, -height, -width, -height + 4.0F);
        draw3DRect(width + 0.2F, -height, width, -height + 4.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    /**
     * Turns the current matrix to face the camera fully (yaw and pitch). Turning only around the vertical axis left
     * the flat ESP frames tilted towards the camera, so they were drawn as trapezoids/parallelograms ("crooked")
     * when looked at from above, below or off-centre; a plane facing the camera is always drawn as a rectangle.
     */
    private static void faceCamera() {
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F, 0.0F, 0.0F);
    }

    public static void drawFake2DESP(EntityPlayer entity, float red, float green, float blue) {
        float x = (float) (RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, WorldRenderPass.partialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX());
        float y = (float) (RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, WorldRenderPass.partialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY());
        float z = (float) (RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, WorldRenderPass.partialTicks()) - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ());
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height / 2.0F, z);
        faceCamera();
        GlStateManager.scale(-0.1F, -0.1F, 0.1F);
        float width = (float) (23.3 * entity.width / 2.0);
        float height = 12.0F;
        // thin dark outline first, so the coloured frame reads on any background
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.6F);
        draw3DRect(width + 0.3F, height - 0.3F, -width - 0.3F, height + 0.7F);
        draw3DRect(width + 0.3F, -height - 0.3F, -width - 0.3F, -height + 0.7F);
        draw3DRect(width + 0.3F, -height + 0.1F, width - 0.7F, height + 0.7F);
        draw3DRect(-width - 0.3F, -height + 0.1F, -width + 0.7F, height + 0.7F);
        GlStateManager.color(red, green, blue);
        draw3DRect(width, height, -width, height + 0.4F);
        draw3DRect(width, -height, -width, -height + 0.4F);
        draw3DRect(width, -height + 0.4F, width - 0.4F, height + 0.4F);
        draw3DRect(-width, -height + 0.4F, -width + 0.4F, height + 0.4F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public static void draw3DRect(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
    }

    private static final float[] PROJECTED = new float[3];

    /**
     * Screen rectangle (GUI units: x1, y1, x2, y2) around an entity's box, or null when it is not entirely in front of
     * the camera. Uses the frame's world camera as Minecraft already read it (no glGet, which can stall the frame), so
     * the current GL matrices do not matter.
     *
     * Every corner has to project: building the rectangle from only the corners in front of the camera (a player
     * right beside or behind you) gave the stretched, flattened, jumping boxes. Such a box is skipped instead.
     */
    public static Vector4d projectToScreen(Entity entity, double screenScale) {
        float partialTicks = WorldRenderPass.partialTicks();
        double ix = lerpDouble(entity.posX, entity.lastTickPosX, partialTicks);
        double iy = lerpDouble(entity.posY, entity.lastTickPosY, partialTicks);
        double iz = lerpDouble(entity.posZ, entity.lastTickPosZ, partialTicks);
        IAccessorRenderManager renderManager = (IAccessorRenderManager) mc.getRenderManager();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(0.1F, 0.1F, 0.1F)
                .offset(ix - entity.posX - renderManager.getRenderPosX(), iy - entity.posY - renderManager.getRenderPosY(),
                        iz - entity.posZ - renderManager.getRenderPosZ());
        Vector4d result = null;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
            if (CameraMatrices.project(x, y, z, PROJECTED) == null) return null;
            double sx = PROJECTED[0] / screenScale;
            double sy = (mc.displayHeight - PROJECTED[1]) / screenScale;
            if (result == null) {
                result = new Vector4d(sx, sy, sx, sy);
            } else {
                result.x = Math.min(sx, result.x);
                result.y = Math.min(sy, result.y);
                result.z = Math.max(sx, result.z);
                result.w = Math.max(sy, result.w);
            }
        }
        // a box many screens wide (camera inside the player's box) is not a box anyone wants drawn
        if (result != null) {
            double limit = 3.0 * Math.max(mc.displayWidth, mc.displayHeight) / screenScale;
            if (result.z - result.x > limit || result.w - result.y > limit) return null;
        }
        return result;
    }

    public static void updateFrustum() {
        try {
            RenderUtil.cameraFrustum = new Frustum();
            Entity view = RenderUtil.mc.getRenderViewEntity();
            if (view != null) {
                RenderUtil.cameraFrustum.setPosition(view.posX, view.posY, view.posZ);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isInViewFrustum(AxisAlignedBB axisAlignedBB, double expand) {
        Entity view = RenderUtil.mc.getRenderViewEntity();
        if (view == null) return true;
        if (RenderUtil.cameraFrustum == null) {
            updateFrustum();
            if (RenderUtil.cameraFrustum == null) return true;
        }
        cameraFrustum.setPosition(view.posX, view.posY, view.posZ);
        return cameraFrustum.isBoundingBoxInFrustum(axisAlignedBB.expand(expand, expand, expand));
    }

    public static void enableRenderState() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
    }

    public static void disableRenderState() {
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void setColor(int argb) {
        float f = (float) (argb >> 24 & 0xFF) / 255.0f;
        float f2 = (float) (argb >> 16 & 0xFF) / 255.0f;
        float f3 = (float) (argb >> 8 & 0xFF) / 255.0f;
        float f4 = (float) (argb & 0xFF) / 255.0f;
        GlStateManager.color(f2, f3, f4, f);
    }

    public static float lerpFloat(float current, float previous, float t) {
        return previous + (current - previous) * t;
    }

    public static double lerpDouble(double current, double previous, double t) {
        return previous + (current - previous) * t;
    }

    public static final class EnchantmentData {
        public final String shortName;
        public final int maxLevel;

        public EnchantmentData(String shortName, int maxLevel) {
            this.shortName = shortName;
            this.maxLevel = maxLevel;
        }
    }

    static final class EnchantmentMap extends HashMap<Integer, EnchantmentData> {
        EnchantmentMap() {
            this.put(0, new EnchantmentData("Pr", 4));
            this.put(1, new EnchantmentData("Fp", 4));
            this.put(2, new EnchantmentData("Ff", 4));
            this.put(3, new EnchantmentData("Bp", 4));
            this.put(4, new EnchantmentData("Pp", 4));
            this.put(5, new EnchantmentData("Re", 3));
            this.put(6, new EnchantmentData("Aq", 1));
            this.put(7, new EnchantmentData("Th", 3));
            this.put(8, new EnchantmentData("Ds", 3));
            this.put(16, new EnchantmentData("Sh", 5));
            this.put(17, new EnchantmentData("Sm", 5));
            this.put(18, new EnchantmentData("BoA", 5));
            this.put(19, new EnchantmentData("Kb", 2));
            this.put(20, new EnchantmentData("Fa", 2));
            this.put(21, new EnchantmentData("Lo", 3));
            this.put(32, new EnchantmentData("Ef", 5));
            this.put(33, new EnchantmentData("St", 1));
            this.put(34, new EnchantmentData("Ub", 3));
            this.put(35, new EnchantmentData("Fo", 3));
            this.put(48, new EnchantmentData("Po", 5));
            this.put(49, new EnchantmentData("Pu", 2));
            this.put(50, new EnchantmentData("Fl", 1));
            this.put(51, new EnchantmentData("Inf", 1));
            this.put(61, new EnchantmentData("LoS", 3));
            this.put(62, new EnchantmentData("Lu", 3));
        }
    }
}