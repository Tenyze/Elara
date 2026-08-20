package elara.module.combat.velocity;

import elara.module.combat.Velocity;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.mixin.IAccessorEntity;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simplified port of Rise's GrimVelocity.
 * <p>
 * Core behavior preserved:
 * - Buffer S12/S32/S14/S08 packets while airborne & a velocity is pending.
 * - Freeze motion on the strafe tick; release buffer on jump or landing.
 * - Simulate a block-place (C08) against the floor while frozen, so GrimAC
 *   treats the player as having placed a block under themselves (canceling
 *   the velocity's vertical component).
 * <p>
 * Removed (Rise-only features):
 * - RotationComponent / MovementFix integration.
 * - ViaVersion 1.19 USE_ITEM_ON path (1.8.9 project).
 * - GrimSpeed fast-fall interop.
 */
public class GrimVelocity {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Velocity parent;

    private boolean movementFrozen = false;
    private boolean velocityPending = false;
    private boolean flushing = false;
    private boolean motionSaved = false;
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;

    private final List<Packet<?>> heldPackets = new ArrayList<>();
    private final Set<BlockPos> placedBlocks = new HashSet<>();

    public GrimVelocity(Velocity parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        // Only act once we've been alive long enough for the buffer to matter.
        if (mc.thePlayer.ticksExisted < 7 || ((IAccessorEntity) mc.thePlayer).getIsInWeb()) return;
        if (this.flushing) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity s12 = (S12PacketEntityVelocity) packet;
            if (s12.getEntityID() == mc.thePlayer.getEntityId()) {
                if (mc.thePlayer.onGround) {
                    this.movementFrozen = true;
                } else {
                    this.heldPackets.add(s12);
                    this.velocityPending = true;
                }
                event.setCancelled(true);
                return;
            }
        }

        if (this.velocityPending) {
            if (packet instanceof S32PacketConfirmTransaction
                    || packet instanceof S14PacketEntity
                    || packet instanceof S08PacketPlayerPosLook) {
                this.heldPackets.add(packet);
                event.setCancelled(true);
            }
        }

        if (packet instanceof S23PacketBlockChange) {
            BlockPos pos = ((S23PacketBlockChange) packet).getBlockPosition();
            if (this.placedBlocks.remove(pos) && this.placedBlocks.isEmpty()) {
                this.movementFrozen = false;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;
        if (parent.onSwing.getValue() && !mc.thePlayer.isSwingInProgress) return;

        // Release path 1: hurtTime expired while still airborne.
        if (mc.thePlayer.ticksExisted >= 7
                && !((IAccessorEntity) mc.thePlayer).getIsInWeb()
                && !mc.thePlayer.onGround
                && mc.thePlayer.hurtTime > 25
                && this.velocityPending) {
            this.velocityPending = false;
            this.flushHeldPackets();
        }

        // Release path 2: touched ground (no longer airborne).
        if (mc.thePlayer.onGround && (!this.heldPackets.isEmpty() || this.movementFrozen || this.velocityPending)) {
            this.movementFrozen = false;
            this.placedBlocks.clear();
            this.velocityPending = false;
            this.motionSaved = false;
            this.flushHeldPackets();
        }

        // Motion save/restore around the frozen window.
        if (this.movementFrozen) {
            if (!this.motionSaved) {
                this.savedMotionX = mc.thePlayer.motionX;
                this.savedMotionY = mc.thePlayer.motionY;
                this.savedMotionZ = mc.thePlayer.motionZ;
                this.motionSaved = true;
            }
            // Simulate placing a block under the player to satisfy GrimAC's
            // "player placed a block, so vertical velocity is canceled" check.
            BlockPos playerPos = new BlockPos(mc.thePlayer);
            BlockPos below = playerPos.down();
            mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(
                    below,
                    EnumFacing.UP.getIndex(),
                    mc.thePlayer.getHeldItem(),
                    (float) (mc.thePlayer.posX - playerPos.getX()),
                    1.0F,
                    (float) (mc.thePlayer.posZ - playerPos.getZ())
            ));
            this.placedBlocks.add(below);
        } else if (this.motionSaved) {
            mc.thePlayer.motionX = this.savedMotionX;
            mc.thePlayer.motionY = this.savedMotionY;
            mc.thePlayer.motionZ = this.savedMotionZ;
            this.motionSaved = false;
        }
    }

    private void flushHeldPackets() {
        if (this.heldPackets.isEmpty()) return;
        this.flushing = true;
        // Preserve horizontal/vertical momentum across the flush, matching Rise's
        // "save motion -> receive buffered packets -> restore motion" pattern.
        double mx = mc.thePlayer.motionX;
        double my = mc.thePlayer.motionY;
        double mz = mc.thePlayer.motionZ;
        for (Packet<?> held : new ArrayList<>(this.heldPackets)) {
            PacketUtil.receivePacket(held);
        }
        this.heldPackets.clear();
        mc.thePlayer.motionX = mx;
        mc.thePlayer.motionY = my;
        mc.thePlayer.motionZ = mz;
        this.flushing = false;
    }

    public void onEnable() {
        this.heldPackets.clear();
        this.placedBlocks.clear();
        this.movementFrozen = false;
        this.velocityPending = false;
        this.motionSaved = false;
    }

    public void onDisable() {
        this.flushHeldPackets();
        this.movementFrozen = false;
        this.velocityPending = false;
        this.motionSaved = false;
        this.placedBlocks.clear();
    }
}
