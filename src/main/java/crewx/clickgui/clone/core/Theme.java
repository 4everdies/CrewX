package crewx.clickgui.clone.core;

/**
 * Medidas e cores do visual (escala ampliada para legibilidade).
 * Referência original era 1280x720; valores abaixo ~1.45x.
 */
public final class Theme {
    // ---------- geometria ----------
    public float marginX = 16f, marginY = 20f;
    public float panelW = 188f, panelGap = 10f;
    public float radius = 10f;
    public float headerH = 28f;
    public float rowH = 26f;
    /** Margem interna dos sliders / settings. */
    public float padX = 10f;
    /** Margem do texto dos módulos/cabeçalho. */
    public float textPadX = 11f;
    /** Distância do centro do ícone até a borda direita do painel. */
    public float iconRight = 18f;

    // settings
    public float sliderH = 42f, boolH = 26f, modeH = 26f, modeOptionH = 20f, settingsPadBottom = 6f;
    public float colorH = 48f, textH = 28f;
    public float labelCy = 12f;
    public float trackCy = 30f;
    public float trackH = 6f, knobR = 4.5f;
    public float textDy = -1f;
    /** Largura mínima reservada para o valor à direita (evita overlap). */
    public float valueReserve = 52f;
    /** Espaço do switch bool à direita. */
    public float switchW = 26f, switchH = 13f;

    // ---------- cores (ARGB) ----------
    public int body = 0xCC1F2329;
    public int header = 0xCC2B3039;
    public int text = 0xFFFFFFFF;
    public int textEnabled = 0xFFFF58D7;
    public int textMuted = 0x99FFFFFF;
    public int hover = 0x12FFFFFF;
    public int track = 0x0EFFFFFF;
    public int accentA = 0xFFFF02FF;
    public int accentB = 0xFFFFAFB0;
    public int knob = 0xFFEDEDEE;
    public int hint = 0x8CC8C8C8;

    // ---------- efeitos ----------
    public boolean backdropBlur = true;
    public float blur = 7f;

    public static int withAlpha(int argb, float mul) {
        int a = Math.round(((argb >>> 24) & 0xFF) * mul);
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int ar = (int) (((a >>> 16) & 0xFF) + (((b >>> 16) & 0xFF) - ((a >>> 16) & 0xFF)) * t);
        int ag = (int) (((a >>> 8) & 0xFF) + (((b >>> 8) & 0xFF) - ((a >>> 8) & 0xFF)) * t);
        int ab = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }
}
