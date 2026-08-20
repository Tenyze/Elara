package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.mixin.IAccessorS12PacketEntityVelocity;
import elara.mixin.IAccessorS27PacketExplosion;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class WatchdogReduceVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public WatchdogReduceVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (parent.onSwing.getValue() && mc.thePlayer != null && !mc.thePlayer.isSwingInProgress) return;

        double hFactor = 0.8;
        double vFactor = 0.8;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;
                accessor.setMotionX((int) (accessor.getMotionX() * hFactor));
                accessor.setMotionY((int) (accessor.getMotionY() * vFactor));
                accessor.setMotionZ((int) (accessor.getMotionZ() * hFactor));
            }
        } else if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            IAccessorS27PacketExplosion accessor = (IAccessorS27PacketExplosion) packet;
            accessor.setMotionX((float) (accessor.getMotionX() * hFactor));
            accessor.setMotionY((float) (accessor.getMotionY() * vFactor));
            accessor.setMotionZ((float) (accessor.getMotionZ() * hFactor));
        }
    }
}
