package crewx.cosmetics;

import crewx.CrewX;
import crewx.module.modules.render.Accessories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import org.lwjgl.opengl.GL11;

public final class CosmeticLayer implements LayerRenderer<AbstractClientPlayer> {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int[] SIDES = {-1, 1};
    private static final float[][] DRAGON_WING_OUTLINE = {
            {0.0F, 0.0F, 0.45F}, {2.1F, -2.8F, 1.30F}, {5.8F, -7.0F, 3.70F},
            {6.6F, -4.6F, 3.30F}, {10.2F, -5.6F, 4.80F}, {9.0F, -2.2F, 4.70F},
            {12.0F, -0.3F, 5.60F}, {9.4F, 1.4F, 4.90F}, {11.0F, 4.7F, 4.50F},
            {8.2F, 4.0F, 3.80F}, {7.5F, 7.3F, 2.70F}, {5.1F, 4.7F, 1.70F},
            {2.2F, 2.7F, 0.82F}
    };
    private static final float[][][] DRAGON_RIBS = {
            {{0.0F, 0.15F, 0.58F}, {2.2F, -1.0F, 1.25F}, {4.8F, -5.1F, 3.10F}, {5.8F, -7.0F, 3.90F}},
            {{0.0F, 0.15F, 0.58F}, {2.7F, -0.7F, 1.35F}, {7.8F, -4.8F, 4.30F}, {10.2F, -5.6F, 5.05F}},
            {{0.0F, 0.15F, 0.58F}, {3.5F, -0.2F, 1.75F}, {8.6F, -1.3F, 4.90F}, {12.0F, -0.3F, 5.85F}},
            {{0.0F, 0.15F, 0.58F}, {3.1F, 0.7F, 1.55F}, {8.2F, 3.6F, 4.10F}, {11.0F, 4.7F, 4.75F}},
            {{0.0F, 0.15F, 0.58F}, {2.0F, 1.2F, 1.20F}, {5.8F, 6.0F, 3.10F}, {7.5F, 7.3F, 2.95F}},
            {{5.8F, -7.0F, 3.85F}, {6.1F, -5.6F, 4.10F}, {6.4F, -5.0F, 3.85F}, {6.6F, -4.6F, 3.45F}},
            {{10.2F, -5.6F, 5.00F}, {9.5F, -4.0F, 5.30F}, {9.0F, -2.8F, 5.00F}, {9.0F, -2.2F, 4.80F}},
            {{12.0F, -0.3F, 5.80F}, {10.8F, 0.3F, 5.55F}, {9.9F, 1.1F, 5.20F}, {9.4F, 1.4F, 5.00F}},
            {{11.0F, 4.7F, 4.70F}, {9.9F, 4.5F, 4.60F}, {8.8F, 4.2F, 4.05F}, {8.2F, 4.0F, 3.90F}}
    };
    private static final float[] DRAGON_RIB_RADII = {0.63F, 0.44F, 0.52F, 0.44F, 0.48F, 0.22F, 0.22F, 0.22F, 0.22F};
    private final RenderPlayer renderer;

    public CosmeticLayer(RenderPlayer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                              float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (player != MC.thePlayer || player.isInvisible() || CrewX.moduleManager == null) return;
        Object module = CrewX.moduleManager.modules.get(Accessories.class);
        if (!(module instanceof Accessories)) return;
        Accessories accessories = (Accessories) module;
        if (!accessories.isEnabled()) return;

        boolean wings = accessories.wings.getValue();
        int head = accessories.headwear.getValue();
        if (!wings && head == 0) return;

        ModelPlayer model = this.renderer.getMainModel();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        try {
            if (wings) this.renderDragonWings(model, accessories.wingColor.getValue(), scale, ageInTicks);
            if (head > 0) this.renderHeadAccessory(player, model, head, scale);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void renderDragonWings(ModelPlayer model, int wingRgb, float scale, float ageInTicks) {
        model.bipedBody.postRender(scale);
        float flap = (float) Math.sin(ageInTicks * 0.30F) * 15.0F;
        for (int side : SIDES) {
            GlStateManager.pushMatrix();
            try {
                // The torso's back face is at roughly +2 model units; move the wing roots
                // past that plane so the membranes and ribs do not render inside the player.
                GlStateManager.translate(side * 2.1F * scale, 3.0F * scale, 2.7F * scale);
                GlStateManager.rotate(-side * (9.0F + flap), 0.0F, 0.0F, 1.0F);
                GlStateManager.rotate(side * 7.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.scale(side * scale, scale, scale);
                this.drawCurvedDragonWing(wingRgb, ageInTicks);
            } finally {
                GlStateManager.popMatrix();
            }
        }
    }

    private void drawCurvedDragonWing(int wingRgb, float ageInTicks) {
        int base = 0xFF000000 | wingRgb & 0x00FFFFFF;
        this.drawWingGlow(base, ageInTicks);
        this.drawCurvedMembrane(DRAGON_WING_OUTLINE, 5.65F, 0.15F, 2.65F, 0.56F,
                base, shadeColor(base, 0.38F), lightenColor(base, 1.38F));
        for (int i = 0; i < DRAGON_RIBS.length; i++) {
            float[][] rib = DRAGON_RIBS[i];
            int color = i < 5 ? lightenColor(base, 1.25F) : shadeColor(base, 0.66F);
            this.drawBezierRib(rib[0], rib[1], rib[2], rib[3], DRAGON_RIB_RADII[i], color);
        }
    }

    private void drawWingGlow(int baseColor, float ageInTicks) {
        int rgb = baseColor & 0x00FFFFFF;
        int alpha = 28 + (int) ((Math.sin(ageInTicks * 0.28F) + 1.0D) * 10.0D);
        int glow = alpha << 24 | rgb;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        for (int i = 0; i < 5; i++) {
            float[][] rib = DRAGON_RIBS[i];
            this.drawBezierRib(rib[0], rib[1], rib[2], rib[3], DRAGON_RIB_RADII[i] * 1.8F, glow);
        }
        float pulse = (float) (0.55D + 0.45D * Math.sin(ageInTicks * 0.42F));
        this.drawWingSpark(12.0F, -0.3F + pulse * 0.35F, 5.85F, 0.72F * pulse, glow);
        this.drawWingSpark(7.5F, 7.3F - pulse * 0.28F, 3.15F, 0.48F * pulse, glow);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawWingSpark(float x, float y, float z, float size, int color) {
        GL11.glBegin(GL11.GL_TRIANGLES);
        this.color(color);
        this.vertex(x, y - size, z);
        this.vertex(x + size * 0.25F, y, z);
        this.vertex(x, y + size, z);
        this.vertex(x, y + size, z);
        this.vertex(x - size * 0.25F, y, z);
        this.vertex(x, y - size, z);
        this.vertex(x - size, y, z);
        this.vertex(x, y + size * 0.25F, z);
        this.vertex(x + size, y, z);
        this.vertex(x + size, y, z);
        this.vertex(x, y - size * 0.25F, z);
        this.vertex(x - size, y, z);
        GL11.glEnd();
    }

    private void drawCurvedMembrane(float[][] outline, float centerX, float centerY, float centerZ,
                                   float thickness, int frontColor, int backColor, int edgeColor) {
        this.color(frontColor);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < outline.length; i++) {
            float[] a = outline[i];
            float[] b = outline[(i + 1) % outline.length];
            this.vertex(centerX, centerY, centerZ);
            this.vertex(a[0], a[1], a[2]);
            this.vertex(b[0], b[1], b[2]);
        }
        GL11.glEnd();

        this.color(backColor);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < outline.length; i++) {
            float[] a = outline[i];
            float[] b = outline[(i + 1) % outline.length];
            this.vertex(centerX, centerY, centerZ - thickness);
            this.vertex(b[0], b[1], b[2] - thickness);
            this.vertex(a[0], a[1], a[2] - thickness);
        }
        GL11.glEnd();

        this.color(edgeColor);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < outline.length; i++) {
            float[] a = outline[i];
            float[] b = outline[(i + 1) % outline.length];
            this.vertex(a[0], a[1], a[2]);
            this.vertex(b[0], b[1], b[2]);
            this.vertex(b[0], b[1], b[2] - thickness);
            this.vertex(a[0], a[1], a[2] - thickness);
        }
        GL11.glEnd();
    }

    private void drawBezierRib(float[] p0, float[] p1, float[] p2, float[] p3, float radius, int color) {
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        this.color(color);
        int steps = 12;
        int sides = 7;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float u = 1.0F - t;
            float x = u * u * u * p0[0] + 3.0F * u * u * t * p1[0] + 3.0F * u * t * t * p2[0] + t * t * t * p3[0];
            float y = u * u * u * p0[1] + 3.0F * u * u * t * p1[1] + 3.0F * u * t * t * p2[1] + t * t * t * p3[1];
            float z = u * u * u * p0[2] + 3.0F * u * u * t * p1[2] + 3.0F * u * t * t * p2[2] + t * t * t * p3[2];
            float dx = 3.0F * u * u * (p1[0] - p0[0]) + 6.0F * u * t * (p2[0] - p1[0]) + 3.0F * t * t * (p3[0] - p2[0]);
            float dy = 3.0F * u * u * (p1[1] - p0[1]) + 6.0F * u * t * (p2[1] - p1[1]) + 3.0F * t * t * (p3[1] - p2[1]);
            float dz = 3.0F * u * u * (p1[2] - p0[2]) + 6.0F * u * t * (p2[2] - p1[2]) + 3.0F * t * t * (p3[2] - p2[2]);
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 0.0001F) length = 1.0F;
            float ux = dx / length, uy = dy / length, uz = dz / length;
            float rx = Math.abs(uz) > 0.88F ? 0.0F : 0.0F;
            float ry = Math.abs(uz) > 0.88F ? 1.0F : 0.0F;
            float rz = Math.abs(uz) > 0.88F ? 0.0F : 1.0F;
            float n1x = uy * rz - uz * ry;
            float n1y = uz * rx - ux * rz;
            float n1z = ux * ry - uy * rx;
            float n1Length = (float) Math.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
            if (n1Length < 0.0001F) n1Length = 1.0F;
            n1x /= n1Length; n1y /= n1Length; n1z /= n1Length;
            float n2x = uy * n1z - uz * n1y;
            float n2y = uz * n1x - ux * n1z;
            float n2z = ux * n1y - uy * n1x;
            for (int j = 0; j <= sides; j++) {
                double angle = Math.PI * 2.0D * j / sides;
                float c = (float) Math.cos(angle) * radius;
                float s = (float) Math.sin(angle) * radius;
                GL11.glVertex3f(x + n1x * c + n2x * s, y + n1y * c + n2y * s,
                        z + n1z * c + n2z * s);
            }
        }
        GL11.glEnd();
    }

    private void renderHeadAccessory(AbstractClientPlayer player, ModelPlayer model, int style, float scale) {
        GlStateManager.pushMatrix();
        try {
            model.bipedHeadwear.postRender(scale);
            // Keep every style outside the skin and helmet volume.
            GlStateManager.translate(0.0F, -1.15F * scale, 0.0F);
            GlStateManager.scale(scale, scale, scale);
            switch (style) {
                case 1: this.drawHalo(); break;
                case 2: this.drawCrown(); break;
                case 3: this.drawBunnyEars(); break;
                case 4: this.drawCatEars(); break;
                case 5: this.drawDemonHorns(); break;
                case 6: this.drawStrawHat(); break;
                case 7: this.drawBlackCap(); break;
                default: break;
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /** Broad, tapered straw hat with radial weave detail rather than stacked block layers. */
    private void drawStrawHat() {
        this.drawStrawBrim();
        this.drawStrawCone();
        this.drawStrawWeave();
    }

    private void drawStrawBrim() {
        int segments = 48;
        float rx = 8.0F;
        float rz = 7.0F;
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0D * i / segments;
            double b = Math.PI * 2.0D * (i + 1) / segments;
            this.color((i & 1) == 0 ? 0xFFE4C462 : 0xFFD7B653);
            this.vertex(0.0F, -8.18F, 0.0F);
            this.vertex(rx * (float) Math.cos(a), -7.98F, rz * (float) Math.sin(a));
            this.vertex(rx * (float) Math.cos(b), -7.98F, rz * (float) Math.sin(b));
        }
        GL11.glEnd();

        GL11.glBegin(GL11.GL_TRIANGLES);
        this.color(0xFFAC873A);
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0D * i / segments;
            double b = Math.PI * 2.0D * (i + 1) / segments;
            this.vertex(0.0F, -8.40F, 0.0F);
            this.vertex(rx * (float) Math.cos(b), -8.22F, rz * (float) Math.sin(b));
            this.vertex(rx * (float) Math.cos(a), -8.22F, rz * (float) Math.sin(a));
        }
        GL11.glEnd();

        GL11.glBegin(GL11.GL_QUADS);
        this.color(0xFFB18A3D);
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0D * i / segments;
            double b = Math.PI * 2.0D * (i + 1) / segments;
            this.vertex(rx * (float) Math.cos(a), -7.98F, rz * (float) Math.sin(a));
            this.vertex(rx * (float) Math.cos(b), -7.98F, rz * (float) Math.sin(b));
            this.vertex(rx * (float) Math.cos(b), -8.22F, rz * (float) Math.sin(b));
            this.vertex(rx * (float) Math.cos(a), -8.22F, rz * (float) Math.sin(a));
        }
        GL11.glEnd();
    }

    private void drawStrawCone() {
        int rings = 7;
        int segments = 32;
        float baseRadius = 5.35F;
        float tipRadius = 0.38F;
        float baseY = -8.20F;
        float tipY = -11.75F;
        GL11.glBegin(GL11.GL_QUADS);
        for (int ring = 0; ring < rings; ring++) {
            float t0 = ring / (float) rings;
            float t1 = (ring + 1) / (float) rings;
            float r0 = baseRadius + (tipRadius - baseRadius) * t0;
            float r1 = baseRadius + (tipRadius - baseRadius) * t1;
            float y0 = baseY + (tipY - baseY) * t0;
            float y1 = baseY + (tipY - baseY) * t1;
            for (int i = 0; i < segments; i++) {
                double a = Math.PI * 2.0D * i / segments;
                double b = Math.PI * 2.0D * (i + 1) / segments;
                int shade = (ring + i / 8) % 3;
                this.color(shade == 0 ? 0xFFE9CD75 : shade == 1 ? 0xFFDEBF63 : 0xFFE5C86E);
                float za = 0.91F;
                this.vertex(r0 * (float) Math.cos(a), y0, r0 * za * (float) Math.sin(a));
                this.vertex(r0 * (float) Math.cos(b), y0, r0 * za * (float) Math.sin(b));
                this.vertex(r1 * (float) Math.cos(b), y1, r1 * za * (float) Math.sin(b));
                this.vertex(r1 * (float) Math.cos(a), y1, r1 * za * (float) Math.sin(a));
            }
        }
        GL11.glEnd();

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        this.color(0xFFF5D982);
        this.vertex(0.0F, tipY - 0.04F, 0.0F);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            this.vertex(tipRadius * (float) Math.cos(angle), tipY, tipRadius * 0.91F * (float) Math.sin(angle));
        }
        GL11.glEnd();
    }

    private void drawStrawWeave() {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0D * i / 12.0D;
            this.color((i & 1) == 0 ? 0x667A5A25 : 0x66FFF0A4);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int step = 0; step <= 6; step++) {
                float t = step / 6.0F;
                float radius = 5.32F - 4.93F * t;
                float y = -8.23F - 3.47F * t;
                GL11.glVertex3f(radius * (float) Math.cos(angle), y,
                        radius * 0.92F * (float) Math.sin(angle));
            }
            GL11.glEnd();
        }

        for (int ring = 1; ring <= 3; ring++) {
            float t = ring / 5.0F;
            float radius = 5.35F - 4.97F * t;
            float y = -8.24F - 3.48F * t;
            GL11.glBegin(GL11.GL_LINE_LOOP);
            this.color((ring & 1) == 0 ? 0x667A5A25 : 0x66FFF0A4);
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2.0D * i / 24.0D;
                GL11.glVertex3f(radius * (float) Math.cos(angle), y,
                        radius * 0.92F * (float) Math.sin(angle));
            }
            GL11.glEnd();
        }

        for (int i = 0; i < 10; i++) {
            double angle = Math.PI * 2.0D * i / 10.0D;
            this.color((i & 1) == 0 ? 0x557A5A25 : 0x55FFF0A4);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3f(5.35F * (float) Math.cos(angle), -8.17F,
                    4.92F * (float) Math.sin(angle));
            GL11.glVertex3f(7.95F * (float) Math.cos(angle), -7.99F,
                    6.97F * (float) Math.sin(angle));
            GL11.glEnd();
        }
    }

    private void drawBlackCap() {
        this.drawBox(-4.35F, -9.0F, -4.25F, 4.35F, -7.25F, 4.25F, 0xFF17191F);
        this.drawBox(-4.55F, -8.0F, -4.8F, 4.55F, -7.25F, -2.6F, 0xFF252932);
        this.drawBox(-4.0F, -9.15F, -4.12F, 4.0F, -8.82F, 4.12F, 0xFF454B56);
    }

    private void drawHalo() {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        // Raised subtly above the previous placement; concentric translucent strokes form the glow.
        this.drawHaloRing(4.8F, -11.05F, 6.0F, 0x20FFD057);
        this.drawHaloRing(4.67F, -11.12F, 3.5F, 0x55FFD66A);
        this.drawHaloRing(4.56F, -11.18F, 1.7F, 0xFFFFE6A1);
    }

    private void drawHaloRing(float radius, float y, float width, int color) {
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        this.color(color);
        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2.0D * i / 48.0D;
            GL11.glVertex3f((float) Math.cos(angle) * radius, y, (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    private void drawCrown() {
        int gold = 0xFFF1B93F;
        int bright = 0xFFFFD75E;
        this.drawBox(-4.35F, -8.72F, -4.45F, 4.35F, -7.62F, -3.45F, gold);
        this.drawBox(-4.35F, -8.72F, 3.45F, 4.35F, -7.62F, 4.45F, gold);
        this.drawBox(-4.45F, -8.72F, -3.45F, -3.45F, -7.62F, 3.45F, gold);
        this.drawBox(3.45F, -8.72F, -3.45F, 4.45F, -7.62F, 3.45F, gold);
        for (float z : new float[]{-4.45F, 4.45F}) {
            this.drawCrownSpike(-2.65F, z, bright);
            this.drawCrownSpike(0.0F, z, bright);
            this.drawCrownSpike(2.65F, z, bright);
        }
    }

    private void drawCrownSpike(float centerX, float z, int color) {
        GL11.glBegin(GL11.GL_TRIANGLES);
        this.color(color);
        this.vertex(centerX - 0.9F, -8.25F, z);
        this.vertex(centerX, -11.4F, z);
        this.vertex(centerX + 0.9F, -8.25F, z);
        GL11.glEnd();
    }

    private void drawBunnyEars() {
        this.drawBox(-3.4F, -17.0F, -1.2F, -1.0F, -7.8F, 1.2F, 0xFFF2D8E3);
        this.drawBox(1.0F, -17.0F, -1.2F, 3.4F, -7.8F, 1.2F, 0xFFF2D8E3);
        this.drawBox(-2.8F, -15.8F, -1.26F, -1.65F, -9.0F, -1.20F, 0xFFE994B7);
        this.drawBox(1.65F, -15.8F, -1.26F, 2.8F, -9.0F, -1.20F, 0xFFE994B7);
    }

    private void drawCatEars() {
        this.drawEar(-3.1F, 0xFFD8DCE4, 0xFFEBA0B6);
        this.drawEar(3.1F, 0xFFD8DCE4, 0xFFEBA0B6);
    }

    private void drawEar(float centerX, int outer, int inner) {
        this.drawBox(centerX - 1.35F, -10.0F, -1.3F, centerX + 1.35F, -7.4F, 1.3F, outer);
        this.drawBox(centerX - 0.80F, -12.1F, -0.95F, centerX + 0.80F, -9.8F, 0.95F, outer);
        this.drawBox(centerX - 0.35F, -10.0F, -1.34F, centerX + 0.35F, -8.1F, -1.29F, inner);
        this.drawBox(centerX - 0.58F, -10.1F, -0.99F, centerX + 0.58F, -9.85F, 0.99F, shadeColor(inner, 0.78F));
    }

    private void drawDemonHorns() {
        this.drawHorn(-3.1F);
        this.drawHorn(3.1F);
    }

    private void drawHorn(float x) {
        float sign = x < 0.0F ? -1.0F : 1.0F;
        this.drawBox(x - 1.0F, -9.2F, -1.5F, x + 1.0F, -7.0F, 1.5F, 0xFF8C3040);
        this.drawBox(x - 0.72F, -11.0F, -1.2F, x + 0.72F, -9.0F, 1.2F, 0xFF8C3040);
        GL11.glBegin(GL11.GL_TRIANGLES);
        this.color(0xFFE9B6BE);
        this.vertex(x - 0.72F, -10.7F, -1.2F);
        this.vertex(x + sign * 1.1F, -13.2F, -0.5F);
        this.vertex(x + 0.72F, -10.7F, 1.2F);
        this.vertex(x - 0.72F, -10.7F, -1.2F);
        this.vertex(x + 0.72F, -10.7F, 1.2F);
        this.vertex(x - sign * 0.25F, -10.7F, 0.0F);
        GL11.glEnd();
    }

    private void drawBox(float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        this.color(color);
        GL11.glBegin(GL11.GL_QUADS);
        this.vertex(x1, y1, z1); this.vertex(x2, y1, z1); this.vertex(x2, y2, z1); this.vertex(x1, y2, z1);
        this.vertex(x2, y1, z2); this.vertex(x1, y1, z2); this.vertex(x1, y2, z2); this.vertex(x2, y2, z2);
        this.vertex(x1, y1, z2); this.vertex(x1, y1, z1); this.vertex(x1, y2, z1); this.vertex(x1, y2, z2);
        this.vertex(x2, y1, z1); this.vertex(x2, y1, z2); this.vertex(x2, y2, z2); this.vertex(x2, y2, z1);
        this.vertex(x1, y1, z2); this.vertex(x2, y1, z2); this.vertex(x2, y1, z1); this.vertex(x1, y1, z1);
        this.vertex(x1, y2, z1); this.vertex(x2, y2, z1); this.vertex(x2, y2, z2); this.vertex(x1, y2, z2);
        GL11.glEnd();
    }

    private int shadeColor(int argb, float factor) {
        int red = (int) ((argb >> 16 & 255) * factor);
        int green = (int) ((argb >> 8 & 255) * factor);
        int blue = (int) ((argb & 255) * factor);
        return argb & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int lightenColor(int argb, float factor) {
        int red = Math.min(255, (int) ((argb >> 16 & 255) * factor));
        int green = Math.min(255, (int) ((argb >> 8 & 255) * factor));
        int blue = Math.min(255, (int) ((argb & 255) * factor));
        return argb & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private void color(int argb) {
        GL11.glColor4f((argb >> 16 & 255) / 255.0F, (argb >> 8 & 255) / 255.0F,
                (argb & 255) / 255.0F, (argb >>> 24) / 255.0F);
    }

    private void vertex(float x, float y, float z) {
        GL11.glVertex3f(x, y, z);
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
