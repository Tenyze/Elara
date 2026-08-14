package elara.module.misc;

import elara.event.EventTarget;
import elara.events.PacketEvent;
import elara.module.Module;
import elara.util.ChatUtil;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public class FlagDetector extends Module {
    public FlagDetector() {
        super("FlagDetector", false, true, "Detects server flags like lagback and teleportation");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled())
            return;

        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            ChatUtil.sendFormatted("&7[&cFlagDetector&7] &fServer flag detected (Lagback)!");
        }
    }
}
