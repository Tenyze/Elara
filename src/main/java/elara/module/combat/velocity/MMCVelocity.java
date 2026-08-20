package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.MoveInputEvent;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.mixin.IAccessorEntityLivingBase;
import elara.util.BadPacketUtil;
import elara.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.MathHelper;

public class MMCVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    private boolean receivedVelocity;
    private int ticksSinceVelocity = 0;

    public MMCVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (event.isCancelled()) return;
        if (mc.thePlayer == null) return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                this.receivedVelocity = true;
                this.ticksSinceVelocity = 0;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        boolean speedEnabled = false;

        if (this.receivedVelocity) {
            this.ticksSinceVelocity++;
        }

        if (mc.thePlayer.onGround && mc.thePlayer.hurtTime > 0) {
            BadPacketUtil.bad(false, true, false, false, false);
        }

        // Rise: ae == 1 means hurtTime == 1 (one tick since hurt)
        if (mc.thePlayer.hurtTime == 1) {
            mc.thePlayer.motionX *= 0.0;
            mc.thePlayer.motionZ *= 0.0;
        }

        if (mc.thePlayer.hurtTime == 1 && (((IAccessorEntityLivingBase) mc.thePlayer).isJumping() || speedEnabled)) {
            mc.thePlayer.motionY -= 9.0;
        }

        // Rise: cqL == 1 (ticks since velocity) && ae < 4 (hurtTime < 4)
        if (this.ticksSinceVelocity == 1 && mc.thePlayer.hurtTime < 4 && MoveUtil.getSpeed() < 0.31) {
            // Rise MoveUtil.moveFlying(0.05) - add 0.05 forward motion
            float yaw = mc.thePlayer.rotationYaw;
            float forward = mc.thePlayer.movementInput.moveForward;
            float strafe = mc.thePlayer.movementInput.moveStrafe;
            if (forward != 0 || strafe != 0) {
                double f = Math.cos(Math.toRadians(yaw + 90));
                double s = Math.sin(Math.toRadians(yaw + 90));
                mc.thePlayer.motionX += (strafe * f + forward * s) * 0.05;
                mc.thePlayer.motionZ += (strafe * s - forward * f) * 0.05;
            }
        }

        if (this.ticksSinceVelocity >= 4) {
            this.receivedVelocity = false;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        // Original Rise onMoveInput is a no-op
    }
}
