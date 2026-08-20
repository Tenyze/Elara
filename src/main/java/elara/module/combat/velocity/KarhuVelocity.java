package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

/**
 * Rise original uses BlockAABBEvent to fake ceiling blocks while hurt.
 * Elara has no BlockAABBEvent - we cancel the velocity packet and apply
 * a manual reduction while hurtTime <= 9.
 */
public class KarhuVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public KarhuVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (event.isCancelled()) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.hurtTime <= 9) {
                    event.setCancelled(true);
                    // Simulate ceiling: kill vertical, damp horizontal
                    mc.thePlayer.motionY *= 0.0;
                    mc.thePlayer.motionX *= 0.6;
                    mc.thePlayer.motionZ *= 0.6;
                }
            }
        } else if (event.getPacket() instanceof S27PacketExplosion) {
            if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.hurtTime <= 9) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;
        // While hurt and within first 9 ticks, damp horizontal motion
        if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.hurtTime <= 9) {
            mc.thePlayer.motionX *= 0.6;
            mc.thePlayer.motionZ *= 0.6;
        }
    }
}
