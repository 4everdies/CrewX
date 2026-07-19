package myau.util.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Shader {

    private static final String VERTEX_SRC = "#version 120\n" +
            "void main(void) {\n" +
            "gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}";

    protected int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    public Shader(String classpath, boolean isClasspath) {
        InputStream in = Shader.class.getResourceAsStream(classpath);
        if (in == null) {
            System.err.println("[CrewX] Shader not found: " + classpath);
            programId = -1;
            return;
        }
        String source = readShader(in);
        createProgram(source);
    }

    protected Shader(String fragment) {
        createProgram(fragment);
    }

    private void createProgram(String fragment) {
        int vert = compileShader(VERTEX_SRC, GL20.GL_VERTEX_SHADER);
        int frag = compileShader(fragment, GL20.GL_FRAGMENT_SHADER);
        if (vert == -1 || frag == -1) {
            programId = -1;
            return;
        }
        this.programId = GL20.glCreateProgram();
        GL20.glAttachShader(this.programId, vert);
        GL20.glAttachShader(this.programId, frag);
        GL20.glLinkProgram(this.programId);
        int status = GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS);
        if (status == 0) {
            System.err.println("[CrewX] Shader link failed: " + GL20.glGetProgramInfoLog(this.programId, 1024));
            this.programId = -1;
        } else {
            onLink();
        }
    }

    protected void onLink() {}

    protected void onUse() {}

    public void use() {
        if (programId == -1) return;
        GL20.glUseProgram(programId);
        onUse();
    }

    public void stop() {
        GL20.glUseProgram(0);
    }

    public final void setUniform(String name) {
        this.uniformLocations.put(name, GL20.glGetUniformLocation(this.programId, name));
    }

    public final int getUniformLocationCached(String name) {
        return this.uniformLocations.get(name);
    }

    public void startProgram() {
        use();
    }

    public void stopProgram() {
        stop();
    }

    public void uniform1i(String name, int i) {
        int loc = getLocation(name);
        if (loc != -1) GL20.glUniform1i(loc, i);
    }

    public void uniform2i(String name, int i, int j) {
        int loc = getLocation(name);
        if (loc != -1) GL20.glUniform2i(loc, i, j);
    }

    public void uniform1f(String name, float f) {
        int loc = getLocation(name);
        if (loc != -1) GL20.glUniform1f(loc, f);
    }

    public void uniform2f(String name, float f, float g) {
        int loc = getLocation(name);
        if (loc != -1) GL20.glUniform2f(loc, f, g);
    }

    public void uniform3f(String name, float f, float g, float h) {
        int loc = getLocation(name);
        if (loc != -1) GL20.glUniform3f(loc, f, g, h);
    }

    public void uniform4f(String name, float f, float g, float h, float i) {
        int loc = getLocation(name);
        if (loc != -1) GL20.glUniform4f(loc, f, g, h, i);
    }

    private int getLocation(String name) {
        if (programId == -1) return -1;
        return GL20.glGetUniformLocation(programId, name);
    }

    public void renderShader(double x, double y, double width, double height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2d(x, y);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2d(x, y + height);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2d(x + width, y + height);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2d(x + width, y);
        GL11.glEnd();
    }

    private static String readShader(InputStream inputStream) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        int compile = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (compile == 0) {
            System.err.println("[CrewX] Shader compile error: " + GL20.glGetShaderInfoLog(shader, 1024));
            return -1;
        }
        return shader;
    }
}
