package crewx.gui;

import crewx.clickgui.render.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;

public final class BackdropBlur {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final String VERTEX_SHADER = "#version 120\nvoid main(){gl_Position=ftransform();gl_TexCoord[0]=gl_MultiTexCoord0;}";
    private static final String FRAGMENT_SHADER =
            "#version 120\n" +
            "uniform sampler2D sourceTexture;uniform vec2 frameSize;uniform vec2 texelSize;" +
            "uniform vec2 panelSize;uniform float cornerRadius;\n" +
            "void main(){" +
            "vec2 p=(gl_TexCoord[0].st-vec2(0.5))*panelSize;" +
            "vec2 q=abs(p)-(panelSize*0.5-vec2(cornerRadius));" +
            "float d=length(max(q,0.0))+min(max(q.x,q.y),0.0)-cornerRadius;" +
            "float mask=1.0-smoothstep(-0.7,0.7,d);" +
            "vec2 uv=gl_FragCoord.xy/frameSize;vec2 o=texelSize*2.0;" +
            "vec3 c=texture2D(sourceTexture,uv).rgb*0.40;" +
            "c+=(texture2D(sourceTexture,uv+vec2(o.x,0.0)).rgb+texture2D(sourceTexture,uv-vec2(o.x,0.0)).rgb+" +
            "texture2D(sourceTexture,uv+vec2(0.0,o.y)).rgb+texture2D(sourceTexture,uv-vec2(0.0,o.y)).rgb)*0.15;" +
            "gl_FragColor=vec4(c,mask);}";

    private static int snapshotTexture;
    private static int textureWidth;
    private static int textureHeight;
    private static int shaderProgram;
    private static int samplerUniform;
    private static int frameSizeUniform;
    private static int texelSizeUniform;
    private static int panelSizeUniform;
    private static int radiusUniform;
    private static boolean captured;
    private static boolean shaderUnavailable;

    private BackdropBlur() {
    }

    public static void beginFrame() {
        captured = false;
    }

    public static void drawRoundedPanel(float x, float y, float width, float height, float radius, int color) {
        if (width <= 0.0F || height <= 0.0F) return;
        float safeRadius = Math.max(0.0F, Math.min(radius, Math.min(width, height) * 0.5F));
        if ((color >>> 24) > 8 && captureFrame() && ensureShader()) {
            drawBlurred(x, y, width, height, safeRadius);
        }
        RoundedUtils.drawRoundedRect(x, y, width, height, color, safeRadius);
    }

    private static boolean captureFrame() {
        if (captured) return snapshotTexture != 0;
        captured = true;
        int width = MC.displayWidth;
        int height = MC.displayHeight;
        if (width <= 0 || height <= 0) return false;

        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (snapshotTexture == 0) snapshotTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshotTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (textureWidth != width || textureHeight != height) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                textureWidth = width;
                textureHeight = height;
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(activeTexture);
        }
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

    private static void drawBlurred(float x, float y, float width, float height, float radius) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
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
            GL11.glPopAttrib();
        }
    }
}
