package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class UniversocraftVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public UniversocraftVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (event.isCancelled()) return;
        if (parent.onSwing.getValue() && mc.thePlayer != null && !mc.thePlayer.isSwingInProgress) return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                event.setCancelled(true);
                mc.thePlayer.motionY = mc.thePlayer.motionY + (0.1 - Math.random() / 100.0);
            }
        } else if (event.getPacket() instanceof S27PacketExplosion) {
            event.setCancelled(true);
            mc.thePlayer.motionY = mc.thePlayer.motionY + (0.1 - Math.random() / 100.0);
        }
    }
}
