package elara.util;

import elara.mixin.IAccessorPlayerControllerMP;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

/**
 * SilentHotbar - ported from Lizz (LiquidBounce) client.
 * Manages silent slot switching by intercepting C09 packets and
 * client-side slot changes, allowing modules to use a different
 * slot than what is displayed to the server.
 */
public class SilentHotbar {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static SilentHotbarState state = null;

    private static int ticksSinceLastUpdate = 0;
    private static Integer originalSlot = null;

    /** Whether the slot was modified this tick */
    public static boolean modifiedThisTick = false;

    /** Set to true when sending C09 directly to prevent double-reset */
    public static boolean ignoreSlotChange = false;

    /** Whether the user pressed a hotbar key */
    public static boolean pressedAtSlot = false;

    /**
     * @return the enforced slot if active, otherwise the player's actual current slot
     */
    public static int getCurrentSlot() {
        return state != null ? state.enforcedSlot : (mc.thePlayer != null ? mc.thePlayer.inventory.currentItem : 0);
    }

    /**
     * Silently switch the player's slot to the given slot.
     *
     * @param requester      the object requesting the switch (usually a Module instance)
     * @param slot           target slot index (0-8)
     * @param ticksUntilReset ticks before auto-reset, or null for manual reset
     * @param immediate      send C09 immediately to sync with server
     * @param render         whether to render the fake slot clientside
     * @param resetManually  only reset when user switches slots themselves
     */
    public static void selectSlotSilently(Object requester, int slot, Integer ticksUntilReset,
                                          boolean immediate, boolean render, boolean resetManually) {
        if (originalSlot == null) {
            originalSlot = mc.thePlayer != null ? mc.thePlayer.inventory.currentItem : 0;
        }

        state = new SilentHotbarState(slot, requester, ticksUntilReset, render, resetManually);
        ticksSinceLastUpdate = 0;
        modifiedThisTick = true;

        if (immediate && mc.playerController != null) {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }
    }

    /**
     * Convenience: select slot silently with no auto-reset and render=true.
     */
    public static void selectSlotSilently(Object requester, int slot, boolean immediate) {
        selectSlotSilently(requester, slot, null, immediate, true, false);
    }

    /**
     * Reset the silent slot back to the player's real slot.
     *
     * @param requester if non-null, only reset if the current state was set by this requester
     * @param immediate send C09 immediately to sync with server
     */
    public static void resetSlot(Object requester, boolean immediate) {
        if (state == null) return;
        if (requester == null || state.requester == requester) {
            state = null;
            originalSlot = null;
            modifiedThisTick = false;

            if (requester != null && immediate && mc.playerController != null) {
                ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
            }
        }
    }

    /**
     * Check if the slot is currently modified by the given requester.
     */
    public static boolean isSlotModified(Object requester) {
        return state != null && state.requester == requester;
    }

    /**
     * Called every tick to update the silent slot timer.
     */
    public static void updateSilentSlot() {
        pressedAtSlot = false;
        modifiedThisTick = false;

        if (state == null) return;

        if (state.resetTicks != null) {
            if (ticksSinceLastUpdate >= state.resetTicks) {
                resetSlot(state.requester, false);
                return;
            }
        }

        ticksSinceLastUpdate++;
    }

    /**
     * Get the slot to render, respecting the silent slot state.
     *
     * @param option if false, always return the real slot
     * @return the slot to render
     */
    public static int renderSlot(boolean option) {
        if (mc.thePlayer == null) return 0;
        int original = mc.thePlayer.inventory.currentItem;
        if (state == null) return original;
        return (option || state.render) ? getCurrentSlot() : original;
    }

    /**
     * Handle a C09PacketHeldItemChange packet sent by the user or a module.
     * If the slot matches the enforced slot, ignore the change.
     */
    public static boolean onSendC09(C09PacketHeldItemChange packet) {
        if (state != null && state.resetManually && packet.getSlotId() != getCurrentSlot()) {
            resetSlot(null, false);
        }
        return false; // don't cancel the packet
    }

    // ---- Internal state ----

    static class SilentHotbarState {
        final int enforcedSlot;
        final Object requester;
        final Integer resetTicks;
        final boolean render;
        final boolean resetManually;

        SilentHotbarState(int enforcedSlot, Object requester, Integer resetTicks, boolean render, boolean resetManually) {
            this.enforcedSlot = enforcedSlot;
            this.requester = requester;
            this.resetTicks = resetTicks;
            this.render = render;
            this.resetManually = resetManually;
        }
    }
}