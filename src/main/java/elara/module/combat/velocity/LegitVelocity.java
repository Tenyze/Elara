package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.MoveInputEvent;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.property.properties.BooleanProperty;
import elara.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class LegitVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final PercentProperty chance = new PercentProperty("Chance", 100);
    public final BooleanProperty legitTiming = new BooleanProperty("Legit Timing", false);

    private boolean jump = false;
    private int ticksSinceVelocity = 0;

    public LegitVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;
        this.jump = false;
        if (this.ticksSinceVelocity > 0) this.ticksSinceVelocity--;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;
        if (this.jump && Math.random() * 100.0 < this.chance.getValue().doubleValue()) {
            mc.thePlayer.movementInput.jump = true;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (event.isCancelled()) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        if (mc.thePlayer.onGround
                && event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()
                    && packet.getMotionY() > 0
                    && (!this.legitTiming.getValue()
                        || mc.thePlayer.hurtTime <= 14
                        || this.ticksSinceVelocity <= 1)) {
                this.jump = true;
                this.ticksSinceVelocity = 0;
            }
        }
    }
}
