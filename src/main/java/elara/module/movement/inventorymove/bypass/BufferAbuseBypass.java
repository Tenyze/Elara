package elara.module.movement.inventorymove.bypass;

import elara.module.movement.InventoryMove;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.TickEvent;
import elara.events.UpdateEvent;
import elara.property.properties.IntProperty;
import elara.util.KeyBindUtil;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0EPacketClickWindow;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferAbuseBypass {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final InventoryMove parent;

    public final IntProperty clicksSetting = new IntProperty("Clicks", 3, 2, 10);
    public final IntProperty amount = new IntProperty("Amount", 5, 1, 10);

    private final Queue<Packet<?>> queuedPackets = new ConcurrentLinkedQueue<>();
    private final KeyBinding[] movementKeys;
    private boolean waitedTick;
    private boolean flushed;
    private int clickCount;

    public BufferAbuseBypass(InventoryMove parent) {
        this.parent = parent;
        this.movementKeys = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindJump
        };
    }

    private boolean isBuffering() {
        return this.clickCount > 0 && this.clickCount % this.clicksSetting.getValue() == 0;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND) return;
        Packet<?> packet = event.getPacket();
        if (packet instanceof C0EPacketClickWindow) {
            if (this.isBuffering() && !this.flushed) {
                event.setCancelled(true);
                this.queuedPackets.add(packet);
                return;
            }
            this.clickCount++;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (this.isBuffering()) {
            if (!this.flushed) {
                if (!this.waitedTick) {
                    this.waitedTick = true;
                } else {
                    for (int i = 0; i < this.amount.getValue(); i++) {
                        PacketUtil.sendPacketNoEvent(new C0EPacketClickWindow());
                    }
                    while (!this.queuedPackets.isEmpty()) {
                        PacketUtil.sendPacketNoEvent(this.queuedPackets.poll());
                    }
                    this.flushed = true;
                }
            }
        } else {
            this.waitedTick = false;
            this.flushed = false;
        }
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
        while (!queuedPackets.isEmpty()) {
            PacketUtil.sendPacketNoEvent(queuedPackets.poll());
        }
        clickCount = 0;
    }
}
