package elara.command.commands;

import elara.Elara;
import elara.command.Command;
import elara.module.Module;
import elara.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class ListCommand extends Command {
    public ListCommand() {
        super(new ArrayList<>(Arrays.asList("list", "l", "modules", "elara")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (!Elara.moduleManager.modules.isEmpty()) {
            ChatUtil.sendFormatted(String.format("%sModules:&r", Elara.clientName));
            for (Module module : Elara.moduleManager.modules.values()) {
                ChatUtil.sendFormatted(String.format("%s»&r %s&r", module.isHidden() ? "&8" : "&7", module.formatModule()));
            }
        }
    }
}
