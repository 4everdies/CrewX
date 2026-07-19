package myau.clickgui.render;

import org.lwjgl.opengl.GL11;

public class ScissorUtils {
    public static void stencilScisor(Runnable scissoredWindow, Runnable scissorDepends, boolean enabled) {
        if(enabled) {
            StencilUtils.initStencil();
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            StencilUtils.bindWriteStencilBuffer();
            scissoredWindow.run();
            StencilUtils.bindReadStencilBuffer(1);
        }
        scissorDepends.run();
        if(enabled) StencilUtils.uninitStencilBuffer();
    }
}
