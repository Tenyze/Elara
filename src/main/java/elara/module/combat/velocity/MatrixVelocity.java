package elara.module.combat.velocity;

import elara.Elara;
import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.StrafeEvent;
import elara.module.movement.Fly;
import elara.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class MatrixVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public MatrixVelocity(Velocity parent) {
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
                mc.thePlayer.motionY = packet.getMotionY() / 8000.0;
                boolean speedEnabled = false;
                if (!speedEnabled && MoveUtil.isMoving()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mc.thePlayer == null) return;
        Fly fly = (Fly) Elara.moduleManager.getModule(Fly.class);
        boolean speedEnabled = false;
        boolean flyEnabled = fly != null && fly.isEnabled();

        // Rise: ae == 1 means hurtTime == 1
        if (!MoveUtil.isMoving() && mc.thePlayer.hurtTime == 1) {
            mc.thePlayer.motionX *= -0.1;
            mc.thePlayer.motionZ *= -0.1;
        } else if (mc.thePlayer.hurtTime == 1 && !speedEnabled) {
            // MoveUtil.strafe() - set motion to base speed
            MoveUtil.setSpeed(MoveUtil.getBaseMoveSpeed());
        }
        if (mc.thePlayer.hurtTime < 6 && speedEnabled) {
            MoveUtil.setSpeed(MoveUtil.getBaseMoveSpeed());
        } else if (mc.thePlayer.hurtTime > 1 && !speedEnabled && mc.thePlayer.hurtTime < 15 && !flyEnabled) {
            mc.thePlayer.motionY -= 0.00348;
        }
        if (mc.thePlayer.hurtTime < 10 && mc.thePlayer.hurtTime > 1) {
            // Rise checks flight class enabled state - omitted noop
        }
    }
}
