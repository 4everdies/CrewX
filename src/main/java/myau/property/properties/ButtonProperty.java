package myau.property.properties;

import com.google.gson.JsonObject;
import myau.property.Property;

/**
 * Propriedade de acao para a ClickGUI. Nao possui estado persistente: ao ser
 * clicada apenas executa o callback registrado pelo modulo/script.
 */
public class ButtonProperty extends Property<Boolean> {
    private final Runnable action;

    public ButtonProperty(String name, Runnable action) {
        super(name, false, null);
        this.action = action;
    }

    public void press() {
        if (this.action != null) {
            this.action.run();
        }
    }

    @Override
    public String getValuePrompt() {
        return "button";
    }

    @Override
    public String formatValue() {
        return "&bclick";
    }

    @Override
    public boolean parseString(String string) {
        return true;
    }

    @Override
    public boolean read(JsonObject jsonObject) {
        return true;
    }

    @Override
    public void write(JsonObject jsonObject) {
        // Action buttons are intentionally not persisted.
    }
}
