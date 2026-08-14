package elara.command.commands;

import elara.Elara;
import elara.command.Command;
import elara.enums.ChatColors;
import elara.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class FriendCommand extends Command {
    public FriendCommand() {
        super(new ArrayList<>(Arrays.asList("friend", "f")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        if (args.size() >= 2) {
            String subCommand = args.get(1).toLowerCase(Locale.ROOT);
            switch (subCommand) {
                case "a":
                case "add":
                    if (args.size() < 3) {
                        ChatUtil.sendFormatted(
                                String.format("%sUsage: .%s add <&oname&r> [&oname&r] ...&r", Elara.clientName, args.get(0).toLowerCase(Locale.ROOT))
                        );
                        return;
                    }
                    for (String name: args.subList(2, args.size())) {
                        String added = Elara.friendManager.add(name);
                        if (added == null) {
                            ChatUtil.sendFormatted(String.format("%s&o%s&r is already in your friend list&r", Elara.clientName, name));
                        } else {
                            ChatUtil.sendFormatted(String.format("%sAdded &o%s&r to your friend list&r", Elara.clientName, added));
                        }
                    }
                    return;
                case "r":
                case "remove":
                    if (args.size() < 3) {
                        ChatUtil.sendFormatted(
                                String.format("%sUsage: .%s remove <&oname&r> [&oname&r] ...&r", Elara.clientName, args.get(0).toLowerCase(Locale.ROOT))
                        );
                        return;
                    }
                    for (String name: args.subList(2, args.size())){
                        String removed = Elara.friendManager.remove(name);
                        if (removed == null) {
                            ChatUtil.sendFormatted(String.format("%s&o%s&r is not in your friend list&r", Elara.clientName, name));
                        } else {
                            ChatUtil.sendFormatted(String.format("%sRemoved &o%s&r from your friend list&r", Elara.clientName, removed));
                        }
                    }
                    return;
                case "l":
                case "list":
                    ArrayList<String> list = Elara.friendManager.getPlayers();
                    if (list.isEmpty()) {
                        ChatUtil.sendFormatted(String.format("%sNo friends&r", Elara.clientName));
                        return;
                    }
                    ChatUtil.sendFormatted(String.format("%sFriends:&r", Elara.clientName));
                    for (String friend : list) {
                        ChatUtil.sendRaw(String.format(ChatColors.formatColor("   &o%s&r"), friend));
                    }
                    return;
                case "c":
                case "clear":
                    Elara.friendManager.clear();
                    ChatUtil.sendFormatted(String.format("%sCleared your friend list&r", Elara.clientName));
                    return;
                default:
                    if (args.size() == 2) {
                        if (Elara.friendManager.isFriend(args.get(1))) {
                            runCommand(new ArrayList<>(Arrays.asList(args.get(0), "remove", args.get(1))));
                        } else {
                            runCommand(new ArrayList<>(Arrays.asList(args.get(0), "add", args.get(1))));
                        }
                        return;
                    }
            }
        }
        ChatUtil.sendFormatted(
                String.format("%sUsage: .%s <&oa(dd)&r/&or(emove)&r/&ol(ist)&r/&oc(lear)&r>&r", Elara.clientName, args.get(0).toLowerCase(Locale.ROOT))
        );
    }
}
