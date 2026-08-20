package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.mixin.IAccessorS12PacketEntityVelocity;
import elara.mixin.IAccessorS27PacketExplosion;
import elara.property.properties.IntProperty;
import elara.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;

public class BufferAbuseVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final PercentProperty horizontal = new PercentProperty("Horizontal", 100);
    public final PercentProperty vertical = new PercentProperty("Vertical", 100);
    public final IntProperty buffer = new IntProperty("Buffer", 1, 1, 3);

    private int amount = 0;

    public BufferAbuseVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (event.isCancelled()) return;
        if (parent.onSwing.getValue() && mc.thePlayer != null && !mc.thePlayer.isSwingInProgress) return;

        double h = horizontal.getValue().doubleValue();
        double v = vertical.getValue().doubleValue();
        int buf = buffer.getValue();

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                if (this.amount < buf) {
                    event.setCancelled(true);
                    this.amount++;
                    return;
                }
                this.amount = 0;
            }
        } else if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            if (this.amount < buf) {
                event.setCancelled(true);
                this.amount++;
                return;
            }
            IAccessorS27PacketExplosion accessor = (IAccessorS27PacketExplosion) packet;
            accessor.setMotionX((float) (accessor.getMotionX() * (h / 100.0)));
            accessor.setMotionY((float) (accessor.getMotionY() * (v / 100.0)));
            accessor.setMotionZ((float) (accessor.getMotionZ() * (h / 100.0)));
            this.amount = 0;
        }
    }

    public void onEnable() {
        this.amount = 0;
    }

    public void onDisable() {
        this.amount = 0;
    }
}
