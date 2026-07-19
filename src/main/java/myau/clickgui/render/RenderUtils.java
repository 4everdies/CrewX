package myau.clickgui.render;

import org.lwjgl.opengl.GL11;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import myau.clickgui.util.PositionUtils;

public class RenderUtils {
    public static void start2D() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
    }
    
    public static void stop2D() {
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }
    
    public static void drawRect(double x, double y, double x1, double y1, int color) {
        if(x > x1) { double j = x; x = x1; x1 = j; }
        if(y > y1) { double j = y; y = y1; y1 = j; }
        start2D();
        color(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x1, y);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x, y1);
        GL11.glEnd();
        stop2D();
    }
    
    public static void drawRect(PositionUtils position, int color) {
        start2D();
        color(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(position.getX(), position.getY());
        GL11.glVertex2d(position.getX()+position.getWidth(), position.getY());
        GL11.glVertex2d(position.getX()+position.getWidth(), position.getY()+position.getHeight());
        GL11.glVertex2d(position.getX(), position.getY()+position.getHeight());
        GL11.glEnd();
        stop2D();
    }
    
    public static void drawOutlinedRect(double x, double y, double x1, double y1, int color, double outlineDepth) {
        if(x > x1) { double j = x; x = x1; x1 = j; }
        if(y > y1) { double j = y; y = y1; y1 = j; }
        start2D();
        color(color);
        GL11.glLineWidth((float) outlineDepth);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x1, y);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x, y1);
        GL11.glEnd();
        stop2D();
    }
    
    public static void drawGradientRect(double x, double y, double x1, double y1, int colorLeftT, int colorRightT, int colorRightB, int colorLeftB) {
        if(x > x1) { double j = x; x = x1; x1 = j; }
        if(y > y1) { double j = y; y = y1; y1 = j; }
        start2D();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        color(colorLeftT); GL11.glVertex2d(x, y);
        color(colorRightT); GL11.glVertex2d(x1, y);
        color(colorRightB); GL11.glVertex2d(x1, y1);
        color(colorLeftB); GL11.glVertex2d(x, y1);
        GL11.glEnd();
        stop2D();
    }
    
    public static void drawLine(double x, double y, double x1, double y1, int endC, double size) {
        if(x > x1) { double j = x; x = x1; x1 = j; }
        if(y > y1) { double j = y; y = y1; y1 = j; }
        start2D();
        GL11.glLineWidth((float) size);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        color(endC); GL11.glVertex2d(x, y);
        color(endC); GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        stop2D();
    }
    
    public static void drawCustomRoundedRect(double x, double y, double x1, double y1, int color, double roundingLT, double roundingRT, double roundingRB, double roundingLB) {
        if(x > x1) { double j = x; x = x1; x1 = j; }
        if(y > y1) { double j = y; y = y1; y1 = j; }
        start2D();
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(color);
        for(double i = 270; i < 360; i+=1) {
            GL11.glVertex2d(x+roundingLT+Math.sin(i * Math.PI / 180) * roundingLT, y+roundingLT-Math.cos(i * Math.PI / 180)* roundingLT);
        }
        for(double i = 0; i < 90; i+=1) {
            GL11.glVertex2d(x1-roundingRT+Math.sin(i * Math.PI / 180) * roundingRT, y+roundingRT-Math.cos(i * Math.PI / 180)* roundingRT);
        }
        for(double i = 90; i < 180; i+=1) {
            GL11.glVertex2d(x1-roundingRB+Math.sin(i * Math.PI / 180) * roundingRB, y1-roundingRB-Math.cos(i * Math.PI / 180)* roundingRB);
        }
        for(double i = 180; i < 270; i+=1) {
            GL11.glVertex2d(x+roundingLB+Math.sin(i * Math.PI / 180) * roundingLB, y1-roundingLB-Math.cos(i * Math.PI / 180)* roundingLB);
        }
        GL11.glEnd();
        stop2D();
    }
    
    public static void drawCircle(double x, double y, double radius, int color) {
        start2D();
        color(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(x, y);
        for (int i = 0; i <= 360; i++) {
            double rad = Math.toRadians(i);
            GL11.glVertex2d(x + Math.sin(rad) * radius, y + Math.cos(rad) * radius);
        }
        GL11.glEnd();
        stop2D();
    }

    public static void drawImage(double x, double y, double width, double height, ResourceLocation image, int color) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager.enableBlend();
        GL11.glDepthMask(false);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        color(color);
        Minecraft.getMinecraft().getTextureManager().bindTexture(image);
        Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0, 0, (int)width, (int)height, (int)width, (int)height);
        GlStateManager.resetColor();
        GL11.glDepthMask(true);
        GlStateManager.disableBlend();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
    
    public static void enableScisor() { GL11.glEnable(GL11.GL_SCISSOR_TEST); }
    public static void disableScisor() { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    
    public static void scissor(ScaledResolution scaledResolution, double x, double y, double width, double height) {
        final int scaleFactor = scaledResolution.getScaleFactor();
        GL11.glScissor((int) Math.round(x * scaleFactor),
                (int) Math.round((scaledResolution.getScaledHeight() - (y + height)) * scaleFactor),
                (int) Math.round(width * scaleFactor), (int) Math.round(height * scaleFactor));
    }
    
    public static void color(int color) {
        float f3 = (float)(color >> 24 & 255) / 255.0F;
        float f = (float)(color >> 16 & 255) / 255.0F;
        float f1 = (float)(color >> 8 & 255) / 255.0F;
        float f2 = (float)(color & 255) / 255.0F;
        GlStateManager.color(f, f1, f2, f3);
    }
}
