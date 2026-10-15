package crewx.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import crewx.mixin.IAccessorMinecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;

/**
 * Lightweight temporal frame blending for Minecraft 1.8.9.
 * All temporary framebuffer, viewport, texture-unit and texture-binding changes are restored.
 */
public final class MotionBlurRenderer {
    private static final Minecraft MC = Minecraft.getMinecraft();

    private static int historyTexture;
    private static int textureWidth;
    private static int textureHeight;
    private static boolean historyValid;
    private static Object lastWorld;
    private static boolean failureLogged;

    private MotionBlurRenderer() {
    }

    public static void reset() {
        historyValid = false;
        lastWorld = MC.theWorld;
    }

    public static void render(float strength) {
        if (strength <= 0.001F || MC.theWorld == null || MC.thePlayer == null
                || !OpenGlHelper.isFramebufferEnabled()) return;

        if (lastWorld != MC.theWorld) {
            historyValid = false;
            lastWorld = MC.theWorld;
        }

        Framebuffer framebuffer = MC.getFramebuffer();
        if (framebuffer == null) return;

        int copyWidth = framebuffer.framebufferWidth;
        int copyHeight = framebuffer.framebufferHeight;
        int backingWidth = Math.max(copyWidth, framebuffer.framebufferTextureWidth);
        int backingHeight = Math.max(copyHeight, framebuffer.framebufferTextureHeight);
        org.lwjgl.opengl.ContextCapabilities capabilities = GLContext.getCapabilities();
        if (!capabilities.OpenGL20 && !capabilities.GL_ARB_texture_non_power_of_two) {
            backingWidth = nextPowerOfTwo(backingWidth);
            backingHeight = nextPowerOfTwo(backingHeight);
        }
        if (copyWidth <= 0 || copyHeight <= 0 || backingWidth <= 0 || backingHeight <= 0) return;

        if (historyTexture == 0 || backingWidth != textureWidth || backingHeight != textureHeight) {
            allocateHistory(backingWidth, backingHeight);
            if (historyTexture == 0) return;
        }

        float alpha = Math.max(0.0F, Math.min(0.65F, strength));
        // Everything we change is saved with glPushAttrib instead of being read back with glGet*: each query makes
        // the CPU wait for the GPU, which turns into hitches when the GPU is busy. The frame is blended into and
        // copied from whatever framebuffer the world is being drawn into, so no framebuffer switch either.
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            if (historyValid) {
                blendHistory(alpha, (float) copyWidth / backingWidth, (float) copyHeight / backingHeight);
            }
            copyCurrentFrame(copyWidth, copyHeight);
            historyValid = true;
        } catch (Throwable error) {
            historyValid = false;
            reportFailure(error);
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static void release() {
        if (historyTexture != 0) {
            try {
                GL11.glDeleteTextures(historyTexture);
            } catch (Throwable ignored) {
            }
        }
        historyTexture = 0;
        textureWidth = 0;
        textureHeight = 0;
        historyValid = false;
        lastWorld = null;
    }

    private static void allocateHistory(int width, int height) {
        release();
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            historyTexture = GL11.glGenTextures();
            if (historyTexture == 0) return;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            textureWidth = width;
            textureHeight = height;
        } catch (Throwable error) {
            release();
            reportFailure(error);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    /** Inside render()'s glPushAttrib: only the matrices need saving here. */
    private static void blendHistory(float alpha, float maxU, float maxV) {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTexture);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(-1.0F, -1.0F);
            GL11.glTexCoord2f(maxU, 0.0F); GL11.glVertex2f(1.0F, -1.0F);
            GL11.glTexCoord2f(maxU, maxV); GL11.glVertex2f(1.0F, 1.0F);
            GL11.glTexCoord2f(0.0F, maxV); GL11.glVertex2f(-1.0F, 1.0F);
            GL11.glEnd();
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
    }

    private static int nextPowerOfTwo(int value) {
        if (value <= 1) return 1;
        int highest = Integer.highestOneBit(value - 1);
        return highest > (1 << 29) ? value : highest << 1;
    }

    /** Inside render()'s glPushAttrib, which restores the texture unit and binding. */
    private static void copyCurrentFrame(int width, int height) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTexture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
    }

    private static void reportFailure(Throwable error) {
        if (failureLogged) return;
        failureLogged = true;
        try {
            ((IAccessorMinecraft) MC).getLogger().warn("Motion Blur renderer failed", error);
        } catch (Throwable ignored) {
            error.printStackTrace();
        }
    }
}
