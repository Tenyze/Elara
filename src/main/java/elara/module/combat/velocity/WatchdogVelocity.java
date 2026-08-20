package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.mixin.IAccessorS12PacketEntityVelocity;
import elara.mixin.IAccessorS27PacketExplosion;
import elara.property.properties.BooleanProperty;
import elara.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class WatchdogVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final PercentProperty horizontal = new PercentProperty("Horizontal", 20);
    public final PercentProperty vertical = new PercentProperty("Vertical", 40);
    public final BooleanProperty avoidFlag = new BooleanProperty("Avoid Flag", true);
    private int groundTick = 0;

    public WatchdogVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (parent.onSwing.getValue() && mc.thePlayer != null && !mc.thePlayer.isSwingInProgress) return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                int h = horizontal.getValue();
                int v = vertical.getValue();
                if (avoidFlag.getValue()) {
                    if (mc.thePlayer.onGround) {
                        h = Math.min(h, 25);
                        v = Math.min(v, 45);
                        groundTick = 3;
                    }
                }
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
        if (groundTick > 0) groundTick--;
        if (avoidFlag.getValue() && mc.thePlayer.onGround && mc.thePlayer.hurtTime > 0 && groundTick > 0) {
            if (Math.random() < 0.3) {
                mc.thePlayer.motionX *= 0.9;
                mc.thePlayer.motionZ *= 0.9;
            }
        }
    }
}
