package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.mixin.IAccessorS12PacketEntityVelocity;
import elara.mixin.IAccessorS27PacketExplosion;
import elara.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class WatchdogPredictionVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final PercentProperty horizontal = new PercentProperty("Horizontal", 15);
    public final PercentProperty vertical = new PercentProperty("Vertical", 35);
    private boolean velocityPending = false;
    private int predictTicks = 0;

    public WatchdogPredictionVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (parent.onSwing.getValue() && mc.thePlayer != null && !mc.thePlayer.isSwingInProgress) return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                velocityPending = true;
                predictTicks = 2;
                int h = horizontal.getValue();
                int v = vertical.getValue();
                IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;
                double hFactor = h / 100.0;
                double vFactor = v / 100.0;
                accessor.setMotionX((int) (accessor.getMotionX() * hFactor));
                accessor.setMotionY((int) (accessor.getMotionY() * vFactor));
                accessor.setMotionZ((int) (accessor.getMotionZ() * hFactor));
            }
        } else if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            int h = horizontal.getValue();
            int v = vertical.getValue();
            IAccessorS27PacketExplosion accessor = (IAccessorS27PacketExplosion) packet;
            double hFactor = h / 100.0;
            double vFactor = v / 100.0;
            accessor.setMotionX((float) (accessor.getMotionX() * hFactor));
            accessor.setMotionY((float) (accessor.getMotionY() * vFactor));
            accessor.setMotionZ((float) (accessor.getMotionZ() * hFactor));
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (predictTicks > 0) {
            predictTicks--;
            if (predictTicks == 0 && mc.thePlayer.onGround && mc.thePlayer.hurtTime > 0) {
                mc.thePlayer.jump();
            }
        }
    }
}
