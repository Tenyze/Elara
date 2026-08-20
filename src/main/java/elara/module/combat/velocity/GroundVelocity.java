package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.MoveInputEvent;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

public class GroundVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final IntProperty delay = new IntProperty("Delay", 1, 0, 20);

    public GroundVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        if (mc.thePlayer.hurtTime == this.delay.getValue()) {
            mc.thePlayer.onGround = true;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.thePlayer == null) return;
        if (mc.thePlayer.hurtTime == this.delay.getValue() + 1) {
            // Elara MoveInputEvent has no setJump, so we can only stop vanilla jump
            // by manipulating movementInput directly
            mc.thePlayer.movementInput.jump = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
    }
}
