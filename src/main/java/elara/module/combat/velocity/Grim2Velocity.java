package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.mixin.IAccessorC03PacketPlayer;
import elara.property.properties.BooleanProperty;
import elara.property.properties.FloatProperty;
import elara.util.ChatUtil;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import java.util.Random;

/**
 * Simplified port of Rise's Grim2Velocity.
 * <p>
 * Preserves the three core behaviors:
 * - Full cardinal rotation bypass (adds tiny noise to perfectly-cardinal rotations).
 * - Fake S08 handling (silently apply server position resets without flagging).
 * - Velocity packet cancellation.
 */
public class Grim2Velocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    public final BooleanProperty fullRotationFix = new BooleanProperty("Full Rotation Fix", true);
    public final BooleanProperty fakeS08 = new BooleanProperty("Fake S08", true);
    public final BooleanProperty cancelVelocity = new BooleanProperty("Cancel Velocity", true);
    public final FloatProperty rotationNoise = new FloatProperty("Rotation Noise", 0.001F, 0.0F, 0.1F);
    public final BooleanProperty debugLog = new BooleanProperty("Debug Log", false);

    private final Random random = new Random();

    public Grim2Velocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (event.getType() == EventType.SEND) {
            if (this.fullRotationFix.getValue() && event.getPacket() instanceof C03PacketPlayer) {
                C03PacketPlayer c03 = (C03PacketPlayer) event.getPacket();
                // Only rotation-bearing packets (C05/C06) carry yaw/pitch worth patching.
                if (c03 instanceof C03PacketPlayer.C05PacketPlayerLook
                        || c03 instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                    float yaw = c03.getYaw();
                    float pitch = c03.getPitch();
                    if (this.isFullCardinal(yaw) || this.isFullCardinal(pitch)) {
                        float noise = this.rotationNoise.getValue();
                        float newYaw = yaw + (this.random.nextBoolean() ? 1 : -1) * noise;
                        float newPitch = pitch + (this.random.nextBoolean() ? 1 : -1) * noise;
                        IAccessorC03PacketPlayer accessor = (IAccessorC03PacketPlayer) c03;
                        C03PacketPlayer replacement;
                        if (c03 instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                            replacement = new C03PacketPlayer.C06PacketPlayerPosLook(
                                    accessor.getX(), accessor.getY(), accessor.getZ(),
                                    newYaw, newPitch, c03.isOnGround()
                            );
                        } else {
                            replacement = new C03PacketPlayer.C05PacketPlayerLook(
                                    newYaw, newPitch, c03.isOnGround()
                            );
                        }
                        event.setCancelled(true);
                        PacketUtil.sendPacketNoEvent(replacement);
                        this.debug("Full rotation bypass");
                    }
                }
            }
        } else if (event.getType() == EventType.RECEIVE) {
            if (this.fakeS08.getValue() && event.getPacket() instanceof S08PacketPlayerPosLook) {
                S08PacketPlayerPosLook s08 = (S08PacketPlayerPosLook) event.getPacket();
                mc.thePlayer.setPosition(s08.getX(), s08.getY(), s08.getZ());
                mc.thePlayer.motionX = 0.0;
                mc.thePlayer.motionY = 0.0;
                mc.thePlayer.motionZ = 0.0;
                event.setCancelled(true);
                this.debug("Fake S08");
            }

            if (this.cancelVelocity.getValue()
                    && event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) event.getPacket();
                if (s12.getEntityID() == mc.thePlayer.getEntityId()) {
                    event.setCancelled(true);
                    this.debug("Velocity cancelled");
                }
            }
        }
    }

    private boolean isFullCardinal(float value) {
        float f = Math.abs(value % 90.0F);
        return f < 0.01F || f > 89.99F;
    }

    private void debug(String message) {
        if (this.debugLog.getValue()) {
            ChatUtil.sendFormatted("&8[&cGrimVelocity2&8] &7" + message);
        }
    }
}
