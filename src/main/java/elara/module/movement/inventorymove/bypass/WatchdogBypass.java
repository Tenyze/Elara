package elara.module.movement.inventorymove.bypass;

import elara.module.movement.InventoryMove;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.util.KeyBindUtil;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;

public class WatchdogBypass {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final InventoryMove parent;
    private final KeyBinding[] movementKeys;
    private int toggleTick = 0;
    private boolean inventoryState = false;

    public WatchdogBypass(InventoryMove parent) {
        this.parent = parent;
        this.movementKeys = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindJump
        };
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.currentScreen instanceof GuiInventory
                || (mc.currentScreen != null
                && !(mc.currentScreen instanceof GuiChat)
                && !(mc.currentScreen instanceof GuiContainerCreative)
                && mc.currentScreen instanceof GuiContainer)) {
            toggleTick++;
            if (toggleTick >= 4) {
                toggleTick = 0;
                if (!inventoryState) {
                    PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
                    inventoryState = true;
                } else {
                    PacketUtil.sendPacketNoEvent(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
                    inventoryState = false;
                }
            }
            for (KeyBinding keybinding : this.movementKeys) {
                KeyBindUtil.setKeyBindState(keybinding.getKeyCode(), KeyBindUtil.isKeyDown(keybinding.getKeyCode()));
            }
        } else {
            toggleTick = 0;
            inventoryState = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
    }

    public void onDisable() {
        if (inventoryState) {
            PacketUtil.sendPacketNoEvent(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
            inventoryState = false;
        }
        toggleTick = 0;
    }
}
