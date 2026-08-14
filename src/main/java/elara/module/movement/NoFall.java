package elara.module.movement;

import com.google.common.base.CaseFormat;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.event.types.Priority;
import elara.events.PacketEvent;
import elara.mixin.IAccessorC03PacketPlayer;
import elara.module.Module;
import elara.property.properties.FloatProperty;
import elara.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

/**
 * NoFall — ported from LiquidBounce Legacy 1.8.9.
 *
 * Modes:
 *   0 SPOOF     — Spoof onGround=true in every C03PacketPlayer while falling.
 *   1 NO_GROUND — Always set onGround=false (server thinks you never touch ground).
 *   2 TRIGGER   — Send a one-shot onGround=true packet when fallDistance exceeds
 *                 the threshold to reset fall distance mid-air.
 */
public class NoFall extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0,
            new String[]{"SPOOF", "NO_GROUND", "TRIGGER"});
    public final FloatProperty distance = new FloatProperty("distance", 3.0F, 0.0F, 20.0F);

    // ---- Trigger state ----
    private boolean triggered = false;

    public NoFall() {
        super("NoFall", false);
    }

    // ================================================================
    //  Packet handling — SPOOF / NO_GROUND / TRIGGER
    // ================================================================

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        // Server setback → reset everything
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S08PacketPlayerPosLook) {
            this.onDisabled();
            return;
        }

        if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) return;
        if (!(event.getPacket() instanceof C03PacketPlayer)) return;
        if (mc.thePlayer == null) return;

        C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();

        switch (this.mode.getValue()) {
            case 0: // SPOOF
                if (mc.thePlayer.fallDistance > 2.0F) {
                    ((IAccessorC03PacketPlayer) packet).setOnGround(true);
                }
                break;

            case 1: // NO_GROUND
                ((IAccessorC03PacketPlayer) packet).setOnGround(false);
                break;

            case 2: // TRIGGER
                if (mc.thePlayer.fallDistance > this.distance.getValue()
                        && !packet.isOnGround()
                        && !this.triggered) {
                    ((IAccessorC03PacketPlayer) packet).setOnGround(true);
                    mc.thePlayer.fallDistance = 0.0F;
                    this.triggered = true;
                }
                // Reset trigger flag once the player is actually on ground or moving up
                if (mc.thePlayer.onGround || mc.thePlayer.motionY >= 0.0) {
                    this.triggered = false;
                }
                break;
        }
    }

    // ================================================================
    //  Lifecycle
    // ================================================================

    @Override
    public void onDisabled() {
        this.triggered = false;
    }

    @Override
    public void verifyValue(String mode) {
        if (this.isEnabled()) {
            this.onDisabled();
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }
}
