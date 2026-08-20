package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.property.properties.IntProperty;
import elara.util.MoveUtil;
import net.minecraft.client.Minecraft;

public class TickVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final IntProperty tickVelocity = new IntProperty("Tick Velocity", 1, 1, 6);

    public TickVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        if (mc.thePlayer.hurtTime == 10 - this.tickVelocity.getValue()) {
            // Rise MoveUtil.stop() - zeroes horizontal motion
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
    }
}
