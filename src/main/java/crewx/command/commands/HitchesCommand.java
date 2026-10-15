package crewx.command.commands;

import crewx.CrewX;
import crewx.command.Command;
import crewx.util.ChatUtil;
import crewx.util.HitchMonitor;

import java.util.ArrayList;
import java.util.Arrays;

/** .hitches [on|off] - shows in chat what each stutter was made of (see HitchMonitor). */
public class HitchesCommand extends Command {
    public HitchesCommand() {
        super(new ArrayList<>(Arrays.asList("hitches", "lagcheck")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        boolean enable = !HitchMonitor.isEnabled();
        if (args.size() >= 2) enable = args.get(1).equalsIgnoreCase("on") || args.get(1).equalsIgnoreCase("true");
        if (!enable && HitchMonitor.isEnabled()) ChatUtil.sendFormatted(CrewX.clientName + "Hitch monitor: " + HitchMonitor.summary() + "&r");
        HitchMonitor.setEnabled(enable);
        ChatUtil.sendFormatted(CrewX.clientName + "Hitch monitor: " + (enable
                ? "&a&lON&r&7 - walk around; each stutter is explained in chat. &f.hitches off&7 to stop.&r"
                : "&c&lOFF&r"));
    }
}
