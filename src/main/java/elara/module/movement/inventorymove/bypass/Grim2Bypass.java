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
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class Grim2Bypass {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final InventoryMove parent;
    private final KeyBinding[] movementKeys;
    private int tickCounter = 0;

    public Grim2Bypass(InventoryMove parent) {
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
            tickCounter++;
            if (tickCounter % 2 == 0) {
                int current = mc.thePlayer.inventory.currentItem;
                PacketUtil.sendPacket(new C09PacketHeldItemChange((current + 1) % 9));
                PacketUtil.sendPacket(new C09PacketHeldItemChange(current));
            }
            for (KeyBinding keybinding : this.movementKeys) {
                KeyBindUtil.setKeyBindState(keybinding.getKeyCode(), KeyBindUtil.isKeyDown(keybinding.getKeyCode()));
            }
        } else {
            tickCounter = 0;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
    }
}
