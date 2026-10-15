package crewx.gui.menu;

import crewx.accountmanager.AccountManager;
import crewx.accountmanager.MushProfileService;
import crewx.accountmanager.auth.Account;
import crewx.accountmanager.utils.PlayerSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Player faces for the menus, drawn as a textured rounded polygon (a circle when the radius is half the size),
 * so they get the shape without stencil buffers or extra shaders. Uses the same skin sources as the
 * Alt Manager: Mush heads for cracked accounts with a known skin, the account's real skin otherwise.
 */
public final class MenuAvatar {
    private static final int SEGMENTS_PER_CORNER = 10;

    private MenuAvatar() {
    }

    public static Account findAccount(String username) {
        if (username == null) return null;
        for (Account account : AccountManager.accounts) {
            if (username.equalsIgnoreCase(account.getUsername())) return account;
        }
        return null;
    }

    public static void draw(String username, Account account, float x, float y, float size, float radius, float alpha) {
        if (account != null && account.isCracked() && !account.getSkinHash().isEmpty()) {
            ResourceLocation head = MushProfileService.getHeadTexture(account.getSkinHash());
            if (head != null) {
                face(head, x, y, size, radius, alpha, 0.0F, 0.0F, 1.0F, 1.0F);
                return;
            }
        }
        ResourceLocation skin = account != null && account.isCracked()
                ? DefaultPlayerSkin.getDefaultSkin(EntityPlayer.getOfflineUUID("Steve"))
                : PlayerSkinCache.getSkin(username, null);
        face(skin, x, y, size, radius, alpha, 8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F);
        face(skin, x, y, size, radius, alpha, 40.0F / 64.0F, 8.0F / 64.0F, 48.0F / 64.0F, 16.0F / 64.0F);
    }

    /** A whole square texture (server icon...) drawn with rounded corners. */
    public static void drawRoundedTexture(ResourceLocation texture, float x, float y, float size, float radius, float alpha) {
        face(texture, x, y, size, radius, alpha, 0.0F, 0.0F, 1.0F, 1.0F);
    }

    private static void face(ResourceLocation texture, float x, float y, float size, float radius, float alpha,
                             float u0, float v0, float u1, float v1) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        float r = Math.max(0.0F, Math.min(radius, size / 2.0F));
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_TEX);
        vertex(wr, x + size / 2.0F, y + size / 2.0F, x, y, size, u0, v0, u1, v1);
        corner(wr, x + size - r, y + r, r, -90.0, x, y, size, u0, v0, u1, v1);
        corner(wr, x + size - r, y + size - r, r, 0.0, x, y, size, u0, v0, u1, v1);
        corner(wr, x + r, y + size - r, r, 90.0, x, y, size, u0, v0, u1, v1);
        corner(wr, x + r, y + r, r, 180.0, x, y, size, u0, v0, u1, v1);
        vertex(wr, x + size - r, y, x, y, size, u0, v0, u1, v1);
        tessellator.draw();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void corner(WorldRenderer wr, float cx, float cy, float r, double startDeg,
                               float x, float y, float size, float u0, float v0, float u1, float v1) {
        for (int i = 0; i <= SEGMENTS_PER_CORNER; i++) {
            double a = Math.toRadians(startDeg + 90.0 * i / SEGMENTS_PER_CORNER);
            vertex(wr, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, x, y, size, u0, v0, u1, v1);
        }
    }

    private static void vertex(WorldRenderer wr, float px, float py, float x, float y, float size,
                               float u0, float v0, float u1, float v1) {
        double u = u0 + (u1 - u0) * (px - x) / size;
        double v = v0 + (v1 - v0) * (py - y) / size;
        wr.pos(px, py, 0.0D).tex(u, v).endVertex();
    }
}
