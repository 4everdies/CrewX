package crewx.command.commands;

import crewx.CrewX;
import crewx.command.Command;
import crewx.enums.ChatColors;
import crewx.module.modules.render.MediaPlayer;
import crewx.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class SpotifyCommand extends Command {
    public SpotifyCommand() {
        super(new ArrayList<>(Arrays.asList("spotify", "sp", "media", "mediaplayer")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        MediaPlayer spotify = CrewX.moduleManager == null ? null : (MediaPlayer) CrewX.moduleManager.modules.get(MediaPlayer.class);
        if (spotify == null) {
            ChatUtil.sendFormatted(CrewX.clientName + "Media Player não está disponível.");
            return;
        }
        if (args.size() < 2) {
            usage();
            return;
        }
        String action = args.get(1).toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "setid":
            case "clientid":
            case "client-id":
                if (args.size() != 3) {
                    ChatUtil.sendFormatted(CrewX.clientName + "Uso: .spotify setid <client-id>");
                    return;
                }
                spotify.clientId.setValue(args.get(2).trim());
                ChatUtil.sendFormatted(CrewX.clientName + "Client ID salvo na configuração do CrewX. Não use nem informe Client Secret.");
                return;
            case "connect":
            case "auth":
            case "login":
                spotify.connect();
                return;
            case "source":
            case "mode":
                if (args.size() != 3 || !spotify.source.parseString(args.get(2))) {
                    ChatUtil.sendFormatted(CrewX.clientName + "Uso: .spotify source <auto|system|spotify>");
                    return;
                }
                ChatUtil.sendFormatted(CrewX.clientName + "Fonte de mídia: " + spotify.source.getModeString());
                return;
            case "status":
                ChatUtil.sendFormatted(CrewX.clientName + "Mídia: " + spotify.getStatus() + ". O card aparece sozinho quando uma música está tocando (module Media Player).");
                return;
            case "toggle":
            case "pause":
            case "play":
            case "resume":
            case "next":
            case "previous":
            case "prev":
                spotify.control(action);
                return;
            case "refresh":
            case "reload":
                spotify.refreshNow();
                return;
            case "logout":
            case "disconnect":
                spotify.logout();
                return;
            default:
                usage();
        }
    }

    private void usage() {
        ChatUtil.sendFormatted(CrewX.clientName + "Uso: .spotify <connect|logout|source <auto|system|spotify>|status|play|pause|toggle|next|previous|refresh|setid <id>>");
    }
}
