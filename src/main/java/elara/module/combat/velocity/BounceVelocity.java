package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.property.properties.BooleanProperty;
import elara.property.properties.IntProperty;
import elara.util.MoveUtil;
import net.minecraft.client.Minecraft;

public class BounceVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final IntProperty tick = new IntProperty("Tick", 0, 0, 6);
    public final BooleanProperty vertical = new BooleanProperty("Vertical", false);
    public final BooleanProperty horizontal = new BooleanProperty("Horizontal", false);

    public BounceVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        if (mc.thePlayer.hurtTime == 9 - tick.getValue()) {
            if (this.horizontal.getValue()) {
                if (MoveUtil.isMoving()) {
                    MoveUtil.setSpeed(MoveUtil.getSpeed());
                } else {
                    mc.thePlayer.motionX *= -1.0;
                    mc.thePlayer.motionZ *= -1.0;
                }
            }
            if (this.vertical.getValue()) {
                mc.thePlayer.motionY *= -1.0;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
    }
}
