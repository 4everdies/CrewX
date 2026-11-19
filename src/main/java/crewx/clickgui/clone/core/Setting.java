package crewx.clickgui.clone.core;

/** Opção configurável de um módulo (slider, toggle ou lista de modos). */
public abstract class Setting {
    public final String name;

    protected Setting(String name) {
        this.name = name;
    }

    /** Altura que a opção ocupa dentro do painel. */
    abstract float height(Theme t);

    /** Se false, some do painel (property com check). Default true. */
    public boolean visible() {
        return true;
    }
}
