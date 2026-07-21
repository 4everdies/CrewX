package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.script.ScriptModule;
import myau.util.ChatUtil;

import java.awt.Desktop;
import java.util.ArrayList;
import java.util.Arrays;

public class ScriptCommand extends Command {
    public ScriptCommand() {
        super(new ArrayList<>(Arrays.asList("script", "scripts", "lua")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (Myau.scriptManager == null) {
            ChatUtil.sendFormatted(Myau.clientName + "Script manager is not initialized.");
            return;
        }

        String action = args.size() >= 2 ? args.get(1).toLowerCase() : "list";

        switch (action) {
            case "help":
            case "?":
                ChatUtil.sendFormatted(Myau.clientName + "Usage:");
                ChatUtil.sendFormatted(" &f.script list &7- list all scripts");
                ChatUtil.sendFormatted(" &f.script load <name> &7- load or reload a specific script");
                ChatUtil.sendFormatted(" &f.script reload &7- reload all scripts from disk");
                ChatUtil.sendFormatted(" &f.script folder &7- open the scripts folder");
                ChatUtil.sendFormatted(" &f.script help &7- show this message");
                break;

            case "load":
                if (args.size() < 3) {
                    ChatUtil.sendFormatted(Myau.clientName + "Usage: .script load <name>");
                    return;
                }
                Myau.scriptManager.loadScript(args.get(2));
                break;

            case "reload":
            case "r":
                Myau.scriptManager.reload();
                break;

            case "folder":
            case "dir":
                try {
                    Desktop.getDesktop().open(Myau.scriptManager.getDirectory());
                } catch (Throwable t) {
                    ChatUtil.sendFormatted(Myau.clientName + "Folder: &f"
                            + Myau.scriptManager.getDirectory().getAbsolutePath());
                }
                break;

            case "list":
            default:
                if (Myau.scriptManager.getModules().isEmpty()) {
                    ChatUtil.sendFormatted(Myau.clientName + "No scripts in &f"
                            + Myau.scriptManager.getDirectory().getAbsolutePath());
                    return;
                }
                ChatUtil.sendFormatted(Myau.clientName + "Scripts:");
                for (ScriptModule module : Myau.scriptManager.getModules().values()) {
                    String status = module.getScript().isLoaded()
                            ? (module.isEnabled() ? "&a&lON" : "&7off")
                            : "&c&lERROR";
                    ChatUtil.sendFormatted(" &f" + module.getName() + " &r(" + status + "&r)");
                    if (!module.getScript().isLoaded() && module.getScript().getError() != null) {
                        ChatUtil.sendFormatted("   &c" + module.getScript().getError());
                    }
                }
                break;
        }
    }
}
