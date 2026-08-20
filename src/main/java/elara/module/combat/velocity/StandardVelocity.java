package elara.module.combat.velocity;

import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.mixin.IAccessorS12PacketEntityVelocity;
import elara.mixin.IAccessorS27PacketExplosion;
import elara.module.combat.Velocity;
import elara.property.properties.BooleanProperty;
import elara.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class StandardVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final PercentProperty horizontal = new PercentProperty("Horizontal", 0);
    public final PercentProperty vertical = new PercentProperty("Vertical", 0);
    public final BooleanProperty explosionIgnore = new BooleanProperty("Explosion Ignore", false);

    private final Velocity parent;
    private boolean flag = false;
    private int counter = 0;

    public StandardVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (event.isCancelled()) return;
        if (parent.onSwing.getValue() && mc.thePlayer != null && !mc.thePlayer.isSwingInProgress) return;

        double h = horizontal.getValue().doubleValue();
        double v = vertical.getValue().doubleValue();
        boolean explIgnore = explosionIgnore.getValue();

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                if (h == 0.0) {
                    if (v != 0.0) {
                        mc.thePlayer.motionY = packet.getMotionY() / 8000.0;
                    }
                    event.setCancelled(true);
                    return;
                }
                IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;
                accessor.setMotionX((int) (accessor.getMotionX() * (h / 100.0)));
                accessor.setMotionY((int) (accessor.getMotionY() * (v / 100.0)));
                accessor.setMotionZ((int) (accessor.getMotionZ() * (h / 100.0)));
            }
        } else if (event.getPacket() instanceof S27PacketExplosion) {
            if (explIgnore) {
                event.setCancelled(true);
                return;
            }
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            IAccessorS27PacketExplosion accessor = (IAccessorS27PacketExplosion) packet;
            accessor.setMotionX((float) (accessor.getMotionX() * (h / 100.0)));
            accessor.setMotionY((float) (accessor.getMotionY() * (v / 100.0)));
            accessor.setMotionZ((float) (accessor.getMotionZ() * (h / 100.0)));
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        // Rise checks Speed class - omit since no real action here
    }

    public Boolean getFlag() {
        return this.flag;
    }

    public void setFlag(Boolean value) {
        this.flag = value;
    }

    public void onEnable() {
        this.counter = 0;
        this.setFlag(false);
    }
}
