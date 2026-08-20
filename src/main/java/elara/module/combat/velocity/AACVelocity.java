package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.MoveInputEvent;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.util.BadPacketUtil;
import net.minecraft.client.Minecraft;

public class AACVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;
    private boolean jump;

    public AACVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        if (mc.thePlayer.onGround && mc.thePlayer.hurtTime > 0
                && !BadPacketUtil.bad(false, true, false, false, false)) {
            mc.thePlayer.motionX *= 0.6;
            mc.thePlayer.motionZ *= 0.6;
        }
        this.jump = false;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;
        if (this.jump) {
            mc.thePlayer.movementInput.jump = true;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
    }
}
