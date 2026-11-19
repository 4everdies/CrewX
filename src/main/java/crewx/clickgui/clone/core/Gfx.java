package crewx.clickgui.clone.core;

/**
 * Abstração mínima de desenho 2D. O núcleo da GUI só conhece esta interface,
 * então o mesmo layout roda em Java2D (preview) e em OpenGL (Minecraft).
 *
 * Coordenadas em pixels de tela (origem no canto superior esquerdo).
 * Cores em ARGB (0xAARRGGBB).
 */
public interface Gfx {

    /** Retângulo com raio independente por canto (tl, tr, br, bl). */
    void roundRect(float x, float y, float w, float h,
                   float tl, float tr, float br, float bl, int argb);

    /** Retângulo arredondado com gradiente horizontal (esquerda -> direita). */
    void roundRectH(float x, float y, float w, float h, float r, int left, int right);

    /** Desfoca o que já foi desenhado atrás da região (opcional; padrão: não faz nada). */
    default void backdropBlur(float x, float y, float w, float h,
                              float tl, float tr, float br, float bl, float radius) {
    }

    /**
     * Desenha texto. {@code cy} é o centro vertical das letras maiúsculas
     * (não da caixa da linha), o que deixa o alinhamento igual em qualquer fonte.
     */
    void text(FontId font, String s, float x, float cy, int argb);

    /** Desenha um glifo (ícone) centralizado em (cx, cy). */
    void glyph(FontId font, String s, float cx, float cy, int argb);

    float textWidth(FontId font, String s);

    /** Empilha uma área de recorte (interseção com a anterior). */
    void pushClip(float x, float y, float w, float h);

    void popClip();

    // ----- atalhos -----

    default void roundRect(float x, float y, float w, float h, float r, int argb) {
        roundRect(x, y, w, h, r, r, r, r, argb);
    }

    default void circle(float cx, float cy, float r, int argb) {
        roundRect(cx - r, cy - r, r * 2, r * 2, r, argb);
    }

    default void textRight(FontId font, String s, float xRight, float cy, int argb) {
        text(font, s, xRight - textWidth(font, s), cy, argb);
    }
}
