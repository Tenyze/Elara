package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.mixin.IAccessorS12PacketEntityVelocity;
import elara.property.properties.BooleanProperty;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

/**
 * Simplified port of Rise's VulcanVelocity.
 * <p>
 * Core behavior preserved:
 * - Cancel the S12 velocity packet (Vulcan requires a tick-perfect cancel).
 * - Apply only the Y motion directly to the player (the horizontal component
 *   is what Vulcan flags as "no knockback", so we keep vertical only).
 * - Send a fresh C06 rotation packet on velocity, so Vulcan's server-side
 *   rotation tracker stays in sync with the cancel.
 * <p>
 * Removed (Rise-only features): stack counting, ping spoofing, backtrack,
 * damage boost, LongJump/Flight/Jesus interop, Scaffold interop.
 */
public class VulcanVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final BooleanProperty alwaysCancelVertical = new BooleanProperty("Always Cancel Vertical", true);
    public final BooleanProperty sendRotationSync = new BooleanProperty("Rotation Sync", true);

    public VulcanVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        if (!(event.getPacket() instanceof S12PacketEntityVelocity)) return;
        S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;

        double motionY = packet.getMotionY() / 8000.0;
        // Vertical-only application: skip when on ground (Vulcan flags vertical
        // cancel on ground) unless the user opts into always-cancel.
        boolean shouldCancelVertical = this.alwaysCancelVertical.getValue()
                || motionY > 0.08
                || mc.thePlayer.hurtTime > 0;

        // Cancel the original packet so Vulcan never sees the velocity applied.
        event.setCancelled(true);

        if (this.sendRotationSync.getValue()) {
            PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C06PacketPlayerPosLook(
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                    mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                    mc.thePlayer.onGround
            ));
        }

        if (shouldCancelVertical) {
            // Apply only the Y component; horizontal is dropped so the player
            // doesn't get pushed back (the core Vulcan velocity bypass).
            mc.thePlayer.motionY = motionY;
            IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;
            accessor.setMotionX(0);
            accessor.setMotionZ(0);
        }
    }
}
