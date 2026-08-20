package elara.module.movement.inventorymove.bypass;

import elara.module.movement.InventoryMove;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.UpdateEvent;
import elara.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.settings.KeyBinding;

public class NormalBypass {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final InventoryMove parent;

    private final KeyBinding[] movementKeys;

    public NormalBypass(InventoryMove parent) {
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
        if (mc.currentScreen != null
                && !(mc.currentScreen instanceof GuiChat)
                && !(mc.currentScreen instanceof GuiContainerCreative)
                && mc.currentScreen instanceof GuiContainer) {
            for (KeyBinding keybinding : this.movementKeys) {
                KeyBindUtil.setKeyBindState(keybinding.getKeyCode(), KeyBindUtil.isKeyDown(keybinding.getKeyCode()));
            }
        }
    }
}
