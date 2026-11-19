package crewx.clickgui.clone.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Um módulo exibido na GUI. Ligue {@link #onToggle} ao seu sistema de módulos. */
public final class ClickModule {
    public final String name;
    public final List<Setting> settings = new ArrayList<Setting>();

    /** Código da tecla de atalho no backend em uso (LWJGL no MC, KeyEvent no AWT); -1 = sem tecla. */
    public int key = -1;
    /** Escondido da lista de módulos ativos do HUD (a GUI só mostra o texto mais apagado). */
    public boolean hidden;
    public boolean expanded;

    private boolean enabled;
    private Consumer<Boolean> onToggle;
    private IntConsumer onKey;
    /** Progresso animado da expansão 0..1 (uso interno). */
    float anim;

    public ClickModule(String name, Setting... settings) {
        this.name = name;
        add(settings);
    }

    public ClickModule add(Setting... s) {
        for (Setting x : s) settings.add(x);
        return this;
    }

    public ClickModule onToggle(Consumer<Boolean> c) {
        this.onToggle = c;
        return this;
    }

    public ClickModule onKey(IntConsumer c) {
        this.onKey = c;
        return this;
    }

    public void setKeyBind(int k) {
        this.key = k;
        if (onKey != null) onKey.accept(k);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean v) {
        if (enabled == v) return;
        enabled = v;
        if (onToggle != null) onToggle.accept(v);
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    /** Atualiza o estado exibido sem disparar o callback (use para sincronizar com o seu sistema de módulos). */
    public void setEnabledSilently(boolean v) {
        enabled = v;
    }

    /** Abre/fecha a lista de settings. {@code instant} pula a animação. */
    public void setExpanded(boolean v, boolean instant) {
        expanded = v && !settings.isEmpty();
        if (instant) anim = expanded ? 1f : 0f;
    }
}
