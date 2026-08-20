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
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;

public class CancelBypass {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final InventoryMove parent;
    private final KeyBinding[] movementKeys;
    private int currentWindowId = -1;

    public CancelBypass(InventoryMove parent) {
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
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND) return;

        if (event.getPacket() instanceof C16PacketClientStatus) {
            C16PacketClientStatus packet = (C16PacketClientStatus) event.getPacket();
            if (packet.getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                event.setCancelled(true);
                currentWindowId = 0;
                PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
            }
        } else if (event.getPacket() instanceof C0DPacketCloseWindow) {
            event.setCancelled(true);
            currentWindowId = -1;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.currentScreen != null
                && !(mc.currentScreen instanceof GuiChat)
                && !(mc.currentScreen instanceof GuiContainerCreative)
                && mc.currentScreen instanceof GuiContainer) {
            for (KeyBinding keybinding : this.movementKeys) {
                KeyBindUtil.setKeyBindState(keybinding.getKeyCode(), KeyBindUtil.isKeyDown(keybinding.getKeyCode()));
            }
        }
    }

    public void onDisable() {
        if (currentWindowId != -1) {
            PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(currentWindowId));
            currentWindowId = -1;
        }
    }
}
