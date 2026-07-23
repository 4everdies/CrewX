package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.IChatComponent;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StaffDetector extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final String[] RANK_KEYWORDS = {
        "ADMIN", "MOD", "HELPER", "STAFF",
        "DONO", "OWNER", "JR.HELPER",
        "MODERADOR", "ADMINISTRADOR", "AJUDANTE", "SUPPORT", "SUPORTE",
        "GAME_MASTER", "GM", "TRIADOR"
    };

    private static final String[][] RANK_TAG_PATTERNS = {
        {"ADMIN", "OWNER", "DONO", "ADMINISTRADOR"},
        {"MOD", "MODERADOR", "MODERATOR", "GAME_MASTER", "GM", "TRIADOR"},
        {"HELPER", "JR.HELPER", "AJUDANTE", "SUPPORT", "SUPORTE"}
    };

    private final Set<String> detected = new HashSet<>();
    private int tickCounter = 0;

    public final BooleanProperty debug = new BooleanProperty("debug", false);
    public final BooleanProperty tabCheck = new BooleanProperty("tab-check", true);
    public final BooleanProperty worldCheck = new BooleanProperty("world-check", true);
    public final BooleanProperty packetCheck = new BooleanProperty("packet-check", true);
    public final BooleanProperty chatCheck = new BooleanProperty("chat-check", true);
    public final BooleanProperty scoreboardCheck = new BooleanProperty("scoreboard-check", true);
    public final BooleanProperty repeat = new BooleanProperty("repeat", false);

    public StaffDetector() {
        super("StaffDetector", false);
    }

    @Override
    public void onDisabled() {
        detected.clear();
    }

    private String stripColors(String text) {
        if (text == null) return null;
        return text.replaceAll("[ยง&][0-9a-fk-orA-FK-OR]", "").trim();
    }

    private String findRank(String text) {
        if (text == null || text.isEmpty()) return null;
        String cleaned = stripColors(text).toUpperCase();
        for (String kw : RANK_KEYWORDS) {
            if (cleaned.contains(kw.toUpperCase())) {
                return kw;
            }
        }
        return null;
    }

    private String extractRankTag(String text) {
        if (text == null) return null;
        String cleaned = stripColors(text).toUpperCase();
        for (String[] group : RANK_TAG_PATTERNS) {
            for (String p : group) {
                String upper = p.toUpperCase();
                if (cleaned.contains("[" + upper + "]")) {
                    return p;
                }
            }
        }
        return null;
    }

    private String extractPlayerName(String rawName, String displayText, String rank) {
        if (rawName != null) {
            String cleanRaw = stripColors(rawName);
            if (cleanRaw.matches("^[a-zA-Z0-9_]{3,16}$")) {
                return cleanRaw;
            }
        }

        String textToSearch = (displayText != null ? stripColors(displayText) : "") + " " + (rawName != null ? stripColors(rawName) : "");
        textToSearch = textToSearch.trim();

        if (rank != null && !textToSearch.isEmpty()) {
            String regex = "(?i)(?:\\[?" + Pattern.quote(rank) + "\\]?)\\s*[:>\\-]?\\s*([a-zA-Z0-9_]{3,16})";
            Matcher matcher = Pattern.compile(regex).matcher(textToSearch);
            if (matcher.find()) {
                return matcher.group(1);
            }

            String reverseRegex = "([a-zA-Z0-9_]{3,16})\\s*[:>\\-]?\\s*\\[?" + Pattern.quote(rank) + "\\]?";
            Matcher reverseMatcher = Pattern.compile(reverseRegex).matcher(textToSearch);
            if (reverseMatcher.find()) {
                return reverseMatcher.group(1);
            }
        }

        if (!textToSearch.isEmpty()) {
            Matcher anyName = Pattern.compile("\\b([a-zA-Z0-9_]{3,16})\\b").matcher(textToSearch);
            while (anyName.find()) {
                String candidate = anyName.group(1);
                if (rank == null || !candidate.equalsIgnoreCase(rank)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private String getDisplayText(IChatComponent component) {
        if (component == null) return null;
        return component.getUnformattedText();
    }

    private String getDisplayTextFormatted(IChatComponent component) {
        if (component == null) return null;
        return component.getFormattedText();
    }

    private void check(String name, String displayText, String source) {
        String cleanDisplay = stripColors(displayText);
        String cleanRawName = stripColors(name);

        String rank = extractRankTag(cleanDisplay);
        if (rank == null) rank = findRank(cleanDisplay);
        if (rank == null && cleanRawName != null) rank = extractRankTag(cleanRawName);
        if (rank == null && cleanRawName != null) rank = findRank(cleanRawName);

        if (rank == null) {
            if (debug.getValue() && cleanDisplay != null && !cleanDisplay.equalsIgnoreCase(cleanRawName)) {
                try {
                    myau.module.modules.Notifications.pushRaw("Staff Detector", "Debug: " + cleanRawName + " -> " + cleanDisplay);
                } catch (Throwable ignored) {}
            }
            return;
        }

        String finalName = extractPlayerName(name, displayText, rank);
        if (finalName == null || finalName.isEmpty()) return;

        if (mc.theWorld != null) {
            AntiBot antiBot = (AntiBot) Myau.moduleManager.modules.get(AntiBot.class);
            if (antiBot != null && antiBot.isEnabled()) {
                EntityPlayer player = mc.theWorld.getPlayerEntityByName(finalName);
                if (player != null && antiBot.isBot(player)) {
                    return;
                }
            }
        }

        String key = finalName.toUpperCase() + ":" + rank;
        if (detected.contains(key)) return;
        detected.add(key);

        try {
            myau.module.modules.Notifications.pushRaw("Staff Detector", "[" + finalName + "] is watching you.");
        } catch (Throwable ignored) {}
    }

    private void scanTab() {
        if (!tabCheck.getValue() || mc.getNetHandler() == null) return;
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            String name = info.getGameProfile().getName();
            IChatComponent display = info.getDisplayName();
            if (display != null) {
                check(name, getDisplayTextFormatted(display), "TAB");
                String plain = getDisplayText(display);
                if (!java.util.Objects.equals(display.getFormattedText(), plain)) {
                    check(name, plain, "TAB");
                }
            } else {
                ScorePlayerTeam team = info.getPlayerTeam();
                if (team != null) {
                    String prefix = team.getColorPrefix();
                    if (prefix != null) check(name, prefix, "TAB");
                    String suffix = team.getColorSuffix();
                    if (suffix != null) check(name, suffix, "TAB");
                }
            }
        }
    }

    private void scanWorld() {
        if (!worldCheck.getValue() || mc.theWorld == null) return;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            String name = player.getName();
            IChatComponent display = player.getDisplayName();
            if (display != null) {
                check(name, getDisplayTextFormatted(display), "World");
                String plain = getDisplayText(display);
                if (!java.util.Objects.equals(display.getFormattedText(), plain)) {
                    check(name, plain, "World");
                }
            }
        }
    }

    private void scanScoreboardTeams() {
        if (!scoreboardCheck.getValue() || mc.theWorld == null) return;
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) return;
        for (ScorePlayerTeam team : scoreboard.getTeams()) {
            String prefix = team.getColorPrefix();
            String suffix = team.getColorSuffix();
            for (String memberName : team.getMembershipCollection()) {
                if (prefix != null) check(memberName, prefix, "SBTeam");
                if (suffix != null) check(memberName, suffix, "SBTeam");
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.RECEIVE) return;

        if (packetCheck.getValue() && event.getPacket() instanceof S38PacketPlayerListItem) {
            S38PacketPlayerListItem packet = (S38PacketPlayerListItem) event.getPacket();
            try {
                for (S38PacketPlayerListItem.AddPlayerData data : packet.getEntries()) {
                    String name = data.getProfile().getName();
                    IChatComponent display = data.getDisplayName();
                    if (display != null) {
                        check(name, getDisplayTextFormatted(display), "Packet");
                        check(name, getDisplayText(display), "Packet");
                    }
                }
            } catch (Exception ignored) {}
        }

        if (chatCheck.getValue() && event.getPacket() instanceof S02PacketChat) {
            S02PacketChat packet = (S02PacketChat) event.getPacket();
            try {
                IChatComponent component = packet.getChatComponent();
                if (component != null) {
                    String formatted = getDisplayTextFormatted(component);
                    check(null, formatted, "Chat");
                }
            } catch (Exception ignored) {}
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        detected.clear();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) return;

        Fly flyModule = (Fly) Myau.moduleManager.modules.get(Fly.class);
        boolean isKaizen = flyModule != null && flyModule.isEnabled() && flyModule.isKaizenMode();

        if (!isKaizen) return;

        tickCounter++;
        if (tickCounter % 20 != 0) return;

        if (!repeat.getValue()) {
            scanTab();
            scanWorld();
            scanScoreboardTeams();
        } else {
            detected.clear();
            scanTab();
            scanWorld();
            scanScoreboardTeams();
        }
    }
}