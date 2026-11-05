package crewx.module.modules.render;

import crewx.module.Module;
import crewx.property.properties.ModeProperty;
import crewx.property.properties.PercentProperty;
import crewx.property.properties.BooleanProperty;

/** Settings used by the chat rendering mixin. Turning this module off restores vanilla chat rendering. */
public final class ChatCustomization extends Module {
    /** 0 = CrewX glass panel, 1 = transparent, 2 = Minecraft's normal line backgrounds. */
    public final ModeProperty background = new ModeProperty("background", 0,
            new String[]{"Glass", "Transparent", "Minecraft"});
    public final PercentProperty opacity = new PercentProperty("opacity", 74, () -> this.background.getValue() == 0);
    public final BooleanProperty messageAnimation = new BooleanProperty("message-animation", true);

    public ChatCustomization() {
        super("Chat", false);
    }
}
