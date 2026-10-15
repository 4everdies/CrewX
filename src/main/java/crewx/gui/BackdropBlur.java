package crewx.gui;

import crewx.clickgui.render.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;

public final class BackdropBlur {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final String VERTEX_SHADER = "#version 120\nvoid main(){gl_Position=ftransform();gl_TexCoord[0]=gl_MultiTexCoord0;}";
    private static final String FRAGMENT_SHADER =
            "#version 120\n" +
            "uniform sampler2D sourceTexture;uniform vec2 frameSize;uniform vec2 texelSize;\n" +
            "uniform vec2 panelSize;uniform float cornerRadius;uniform float blurRadius;uniform float feather;uniform float opacity;uniform float sceneOverlay;\n" +
            "void main(){" +
            "vec2 p=(gl_TexCoord[0].st-vec2(0.5))*panelSize;" +
            "vec2 q=abs(p)-(panelSize*0.5-vec2(cornerRadius));" +
            "float d=length(max(q,0.0))+min(max(q.x,q.y),0.0)-cornerRadius;" +
            "float mask=1.0-smoothstep(-0.7,0.7,d);" +
            "if(feather>0.0){mask=1.0-smoothstep(-feather,0.0,d);}" +
            "vec2 uv=gl_FragCoord.xy/frameSize;vec2 o=texelSize*blurRadius*0.25;" +
            "vec3 c=vec3(0.0);float tw=0.0;" +
            "for(int i=-2;i<=2;i++){for(int j=-2;j<=2;j++){" +
            "float w=(3.0-abs(float(i)))*(3.0-abs(float(j)));" +
            "c+=texture2D(sourceTexture,uv+vec2(float(i),float(j))*o).rgb*w;tw+=w;}}" +
            "vec3 blurred=c/tw;vec3 scene=texture2D(sourceTexture,uv).rgb;" +
            "gl_FragColor=vec4(mix(blurred,scene,sceneOverlay),mask*opacity);}";

    private static int snapshotTexture;
    private static int textureWidth;
    private static int textureHeight;
    private static int shaderProgram;
    private static int samplerUniform;
    private static int frameSizeUniform;
    private static int texelSizeUniform;
    private static int panelSizeUniform;
    private static int radiusUniform;
    private static int blurRadiusUniform;
    private static int featherUniform;
    private static int opacityUniform;
    private static int sceneOverlayUniform;
    /** Texture storage checked this frame (size follows the window). */
    private static boolean captured;
    /** Window-pixel rectangles {x0, y0, x1, y1} already copied into the snapshot this frame. */
    private static final int[][] COPIED = new int[16][4];
    private static int copiedCount;
    private static boolean shaderUnavailable;

    private BackdropBlur() {
    }

    public static void beginFrame() {
        captured = false;
        copiedCount = 0;
    }

    public static void drawRoundedPanel(float x, float y, float width, float height, float radius, int color) {
        drawRoundedPanel(x, y, width, height, radius, color, 4.0F);
    }

    public static void drawRoundedPanel(float x, float y, float width, float height, float radius, int color, float blurRadius) {
        if (width <= 0.0F || height <= 0.0F) return;
        float safeRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
        if ((color >>> 24) > 8 && enabled() && capture(x, y, width, height, blurRadius) && ensureShader()) {
            drawBlurred(x, y, width, height, safeRadius, Math.max(0.0F, blurRadius), 0.0F);
        }
        RoundedUtils.drawRoundedRect(x, y, width, height, color, safeRadius);
    }

    public static void drawRoundedBlurOnly(float x, float y, float width, float height,
                                           float radius, float blurRadius, float opacity) {
        if (width <= 0.0F || height <= 0.0F || opacity <= 0.0F || !enabled()) return;
        float safeRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
        if (capture(x, y, width, height, blurRadius) && ensureShader()) {
            drawBlurred(x, y, width, height, safeRadius, Math.max(0.0F, blurRadius), 0.0F,
                    Math.min(1.0F, opacity), 0.12F);
        }
    }

    /**
     * Blurred backdrop that fades out smoothly past the edge of the given rectangle: the area extends
     * {@code feather} units beyond it and its opacity falls from 1 (at the rectangle) to 0 (outside).
     */
    public static void drawFeatheredHalo(float x, float y, float width, float height, float radius,
                                         float blurRadius, float feather) {
        if (width <= 0.0F || height <= 0.0F || feather <= 0.0F || !enabled()) return;
        if (capture(x - feather, y - feather, width + feather * 2.0F, height + feather * 2.0F, blurRadius) && ensureShader()) {
            float safeRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
            drawBlurred(x - feather, y - feather, width + feather * 2.0F, height + feather * 2.0F,
                    safeRadius + feather, Math.max(0.0F, blurRadius), feather);
        }
    }

    /**
     * Set while a caller draws under a transform of its own (the chat is translated and scaled): its panel's
     * window position is not known without asking the driver for the matrix, so the whole window is copied.
     */
    private static boolean unknownTransform;

    public static void beginUnknownTransform() {
        unknownTransform = true;
    }

    public static void endUnknownTransform() {
        unknownTransform = false;
    }

    /** "Blur" off in the HUD settings: panels are drawn translucent only, with no GPU work for the blur. */
    private static boolean enabled() {
        if (crewx.CrewX.moduleManager == null) return true;
        crewx.module.modules.render.HUD hud = (crewx.module.modules.render.HUD) crewx.CrewX.moduleManager.modules.get(crewx.module.modules.render.HUD.class);
        return hud == null || hud.blur.getValue();
    }

    /**
     * Copies the part of the frame a panel reads (its rectangle plus the blur reach) into the snapshot texture, at
     * the same position; regions already copied this frame are reused. Small regions matter on weaker GPUs: a
     * full-window copy every frame is a lot of memory traffic for a few small panels.
     *
     * No glGet* here, on purpose: every state query makes the CPU wait until the driver has caught up with all
     * queued work, which is short when the GPU is idle and 50-100 ms when it is busy. Panel positions are worked
     * out from GUI coordinates (callers draw without transforms of their own, except the chat - see
     * {@link #beginUnknownTransform}), and the touched state is saved with glPushAttrib.
     */
    private static boolean capture(float x, float y, float width, float height, float blurRadius) {
        if (!ensureStorage()) return false;
        int displayWidth = MC.displayWidth;
        int displayHeight = MC.displayHeight;
        if (displayWidth <= 0 || displayHeight <= 0) return false;
        int x0, y0, x1, y1;
        if (unknownTransform) {
            x0 = 0;
            y0 = 0;
            x1 = displayWidth;
            y1 = displayHeight;
        } else {
            float factor = new ScaledResolution(MC).getScaleFactor();
            float margin = blurRadius * 0.5F + 3.0F;
            x0 = Math.max(0, (int) Math.floor(x * factor - margin));
            x1 = Math.min(displayWidth, (int) Math.ceil((x + width) * factor + margin));
            // GUI y grows downwards, window pixels upwards
            y0 = Math.max(0, (int) Math.floor(displayHeight - (y + height) * factor - margin));
            y1 = Math.min(displayHeight, (int) Math.ceil(displayHeight - y * factor + margin));
        }
        if (x1 <= x0 || y1 <= y0) return false;
        for (int i = 0; i < copiedCount; i++) {
            int[] c = COPIED[i];
            if (x0 >= c[0] && y0 >= c[1] && x1 <= c[2] && y1 <= c[3]) return true;
        }
        if (GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH)
                >= GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH)) return false;
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);   // active unit and bindings
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshotTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x0, y0, x0, y0, x1 - x0, y1 - y0);
        } catch (Throwable ignored) {
            return false;
        } finally {
            GL11.glPopAttrib();
        }
        if (copiedCount < COPIED.length) {
            int[] c = COPIED[copiedCount++];
            c[0] = x0;
            c[1] = y0;
            c[2] = x1;
            c[3] = y1;
        }
        return true;
    }

    /** (Re)allocates the snapshot texture when the window size changes; once per frame. */
    private static boolean ensureStorage() {
        if (captured) return snapshotTexture != 0;
        captured = true;
        int width = MC.displayWidth;
        int height = MC.displayHeight;
        if (width <= 0 || height <= 0) return false;
        boolean npot = supportsNpotTextures();
        int storageWidth = npot ? width : nextPowerOfTwo(width);
        int storageHeight = npot ? height : nextPowerOfTwo(height);
        if (snapshotTexture != 0 && textureWidth == storageWidth && textureHeight == storageHeight) return true;

        if (GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH)
                >= GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH)) return false;
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if (snapshotTexture == 0) snapshotTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshotTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, storageWidth, storageHeight, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            textureWidth = storageWidth;
            textureHeight = storageHeight;
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static boolean supportsNpotTextures() {
        org.lwjgl.opengl.ContextCapabilities capabilities = GLContext.getCapabilities();
        return capabilities.OpenGL20 || capabilities.GL_ARB_texture_non_power_of_two;
    }

    private static int nextPowerOfTwo(int value) {
        if (value <= 1) return 1;
        int highest = Integer.highestOneBit(value - 1);
        return highest > (1 << 29) ? value : highest << 1;
    }

    private static boolean ensureShader() {
        if (shaderProgram != 0) return true;
        if (shaderUnavailable) return false;
        int vertex = 0;
        int fragment = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
            fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            shaderProgram = GL20.glCreateProgram();
            GL20.glAttachShader(shaderProgram, vertex);
            GL20.glAttachShader(shaderProgram, fragment);
            GL20.glLinkProgram(shaderProgram);
            if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(shaderProgram, 4096));
            }
            samplerUniform = GL20.glGetUniformLocation(shaderProgram, "sourceTexture");
            frameSizeUniform = GL20.glGetUniformLocation(shaderProgram, "frameSize");
            texelSizeUniform = GL20.glGetUniformLocation(shaderProgram, "texelSize");
            panelSizeUniform = GL20.glGetUniformLocation(shaderProgram, "panelSize");
            radiusUniform = GL20.glGetUniformLocation(shaderProgram, "cornerRadius");
            blurRadiusUniform = GL20.glGetUniformLocation(shaderProgram, "blurRadius");
            featherUniform = GL20.glGetUniformLocation(shaderProgram, "feather");
            opacityUniform = GL20.glGetUniformLocation(shaderProgram, "opacity");
            sceneOverlayUniform = GL20.glGetUniformLocation(shaderProgram, "sceneOverlay");
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            return true;
        } catch (Throwable ignored) {
            shaderUnavailable = true;
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
            if (shaderProgram != 0) {
                GL20.glDeleteProgram(shaderProgram);
                shaderProgram = 0;
            }
            return false;
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    private static void drawBlurred(float x, float y, float width, float height, float radius, float blurRadius, float feather) {
        drawBlurred(x, y, width, height, radius, blurRadius, feather, 1.0F, 0.0F);
    }

    private static void drawBlurred(float x, float y, float width, float height, float radius,
                                    float blurRadius, float feather, float opacity, float sceneOverlay) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        if (GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH)
                >= GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH)) return;
        boolean attribPushed = false;
        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attribPushed = true;
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshotTexture);
            GL20.glUseProgram(shaderProgram);
            GL20.glUniform1i(samplerUniform, 0);
            GL20.glUniform2f(frameSizeUniform, (float) textureWidth, (float) textureHeight);
            GL20.glUniform2f(texelSizeUniform, 1.0F / textureWidth, 1.0F / textureHeight);
            GL20.glUniform2f(panelSizeUniform, width, height);
            GL20.glUniform1f(radiusUniform, radius);
            GL20.glUniform1f(blurRadiusUniform, blurRadius);
            GL20.glUniform1f(featherUniform, feather);
            GL20.glUniform1f(opacityUniform, opacity);
            GL20.glUniform1f(sceneOverlayUniform, sceneOverlay);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(1.0F, 0.0F);
            GL11.glVertex2f(x + width, y);
            GL11.glTexCoord2f(1.0F, 1.0F);
            GL11.glVertex2f(x + width, y + height);
            GL11.glTexCoord2f(0.0F, 1.0F);
            GL11.glVertex2f(x, y + height);
            GL11.glEnd();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        } catch (Throwable ignored) {
        } finally {
            GL20.glUseProgram(previousProgram);
            GL13.glActiveTexture(activeTexture);
            if (attribPushed) GL11.glPopAttrib();
        }
    }
}
