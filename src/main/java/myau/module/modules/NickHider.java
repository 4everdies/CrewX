package myau.module.modules;

import myau.enums.ChatColors;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.TextProperty;
import net.minecraft.client.Minecraft;

public class NickHider extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final TextProperty protectName = new TextProperty("name", "You");
    public final BooleanProperty scoreboard = new BooleanProperty("scoreboard", true);
    public final BooleanProperty level = new BooleanProperty("level", true);
    public final BooleanProperty ranks = new BooleanProperty("ranks", true);
    public final TextProperty customTag = new TextProperty("tag", "&4[CREW] &r");
    public final BooleanProperty nickMode = new BooleanProperty("nick-mode", false);

    public NickHider() {
        super("NickHider", false, true);
    }

    public String getRankPrefix() {
        if (!this.ranks.getValue()) return "";
        return ChatColors.formatColor(this.customTag.getValue());
    }

    public String replaceNick(String input) {
        if (input != null && mc.thePlayer != null) {
            if (this.scoreboard.getValue() && input.matches("§7\\d{2}/\\d{2}/\\d{2}(?:\\d{2})?  ?§8.*")) {
                input = input.replaceAll("§8", "§8§k").replaceAll("[^\\x00-\\x7F§]", "?");
            }
            String rankPrefix = getRankPrefix();
            String playerName = mc.thePlayer.getName();
            if (!rankPrefix.isEmpty() && input.contains(playerName)) {
                String replacement;
                if (this.nickMode.getValue()) {
                    replacement = rankPrefix + ChatColors.formatColor(this.protectName.getValue());
                } else {
                    replacement = rankPrefix + playerName;
                }
                input = input.replace(playerName, replacement);
            } else if (this.nickMode.getValue()) {
                input = input.replace(playerName, ChatColors.formatColor(this.protectName.getValue()));
            }
            return input;
        } else {
            return input;
        }
    }
}
