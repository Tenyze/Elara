package elara.module.movement;

import com.google.common.base.CaseFormat;
import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.event.types.Priority;
import elara.events.PacketEvent;
import elara.events.TickEvent;
import elara.events.UpdateEvent;
import elara.mixin.IAccessorC0DPacketCloseWindow;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.module.movement.inventorymove.bypass.InventoryMoveModes;
import elara.property.properties.BooleanProperty;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import elara.util.KeyBindUtil;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InventoryMove extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static final String[] MODE_LIST = new String[]{
            "Normal", "BufferAbuse", "Cancel", "Grim2", "Watchdog"
    };

    public final ModeProperty mode = new ModeProperty("Mode", 0, MODE_LIST);
    public final BooleanProperty guiEnabled = new BooleanProperty("click-gui", true);
    public final IntProperty openDelay = new IntProperty("open-delay", 0, 0, 20, () -> mode.getValue() == 0);
    public final IntProperty closeDelay = new IntProperty("close-delay", 4, 0, 20, () -> mode.getValue() == 0);
    public final BooleanProperty lockMoveKey = new BooleanProperty("lock-move-dey", false, () -> mode.getValue() == 0);

    private final InventoryMoveModes bypass = new InventoryMoveModes(this);

    private final Queue<C0EPacketClickWindow> clickQueue = new ConcurrentLinkedQueue<>();
    private boolean keysPressed = false;
    private C16PacketClientStatus pendingStatus = null;
    private int delayTicks = 0;
    private int openDelayTicks = -1;
    private int closeDelayTicks = -1;
    private final Map<KeyBinding, Boolean> movementKeys = new HashMap<KeyBinding, Boolean>(8) {{
        put(mc.gameSettings.keyBindForward, false);
        put(mc.gameSettings.keyBindBack, false);
        put(mc.gameSettings.keyBindLeft, false);
        put(mc.gameSettings.keyBindRight, false);
        put(mc.gameSettings.keyBindJump, false);
        put(mc.gameSettings.keyBindSneak, false);
        put(mc.gameSettings.keyBindSprint, false);
    }};

    public InventoryMove() {
        super("InventoryMove", false, false, "", ModuleCategory.MOVEMENT);
    }

    public void pressMovementKeys(boolean skipSneak) {
        this.movementKeys.keySet().stream()
                .filter(key -> !skipSneak || key != mc.gameSettings.keyBindSneak)
                .forEach(key -> KeyBindUtil.updateKeyState(key.getKeyCode()));
        if (Elara.moduleManager.getModule(Sprint.class).isEnabled()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        this.keysPressed = true;
    }

    public void resetMovementKeys() {
        this.movementKeys.replaceAll((k, v) -> false);
    }

    public boolean isSetMovementKeys() {
        return this.movementKeys.values().stream().anyMatch(Boolean::booleanValue);
    }

    public void storeMovementKeys() {
        this.movementKeys.replaceAll((k, v) -> KeyBindUtil.isKeyDown(k.getKeyCode()));
    }

    public void restoreMovementKeys() {
        for (Map.Entry<KeyBinding, Boolean> keyBinding : movementKeys.entrySet()) {
            KeyBindUtil.setKeyBindState(keyBinding.getKey().getKeyCode(), keyBinding.getValue());
        }
        if (Elara.moduleManager.getModule(Sprint.class).isEnabled()) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
        }
        this.keysPressed = true;
    }

    public boolean canInvWalk() {
        if (!(mc.currentScreen instanceof GuiContainer)) return false;
        if (mc.currentScreen instanceof GuiContainerCreative) return false;
        return true;
    }

    public boolean temporaryStackIsEmpty() {
        if (mc.thePlayer.inventory.getItemStack() != null) return false;
        if (mc.thePlayer.inventoryContainer instanceof ContainerPlayer) {
            ContainerPlayer containerPlayer = (ContainerPlayer)mc.thePlayer.inventoryContainer;
            for (int i = 0; i < containerPlayer.craftMatrix.getSizeInventory(); i++) {
                ItemStack stack = containerPlayer.craftMatrix.getStackInSlot(i);
                if (stack != null) {
                    return false;
                }
            }
        }
        return true;
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.openDelayTicks >= 0) {
                this.openDelayTicks--;
                return;
            }
            while (!this.clickQueue.isEmpty()) {
                PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
            }
            if (this.closeDelayTicks > 0) {
                if (this.temporaryStackIsEmpty()) {
                    this.closeDelayTicks--;
                }
            } else if (this.closeDelayTicks == 0) {
                if (mc.currentScreen instanceof GuiInventory)
                    PacketUtil.sendPacketNoEvent(new C0DPacketCloseWindow(0));
                this.closeDelayTicks = -1;
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        if (mc.currentScreen instanceof elara.ui.ClickGui && this.guiEnabled.getValue()) {
            this.pressMovementKeys(true);
            return;
        }

        if (this.mode.getValue() == 0) {
            if (this.canInvWalk()) {
                if (this.isSetMovementKeys() && this.lockMoveKey.getValue()) {
                    this.restoreMovementKeys();
                } else {
                    this.pressMovementKeys(true);
                }
            } else {
                if (this.keysPressed) {
                    if (mc.currentScreen != null) {
                        KeyBinding.unPressAllKeys();
                    } else if (this.isSetMovementKeys()) {
                        this.resetMovementKeys();
                        this.pressMovementKeys(false);
                    }
                    this.keysPressed = false;
                }
                if (this.pendingStatus != null) {
                    PacketUtil.sendPacketNoEvent(this.pendingStatus);
                    this.pendingStatus = null;
                }
                if (this.delayTicks > 0) {
                    this.delayTicks--;
                }
            }
        } else {
            bypass.onUpdate(event);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;

        if (this.mode.getValue() == 0) {
            if (event.getType() != EventType.SEND) return;

            if (event.getPacket() instanceof C16PacketClientStatus) {
                this.storeMovementKeys();
            } else if (!(event.getPacket() instanceof C0EPacketClickWindow)) {
                if (event.getPacket() instanceof C0DPacketCloseWindow) {
                    C0DPacketCloseWindow packet = (C0DPacketCloseWindow) event.getPacket();
                    if (((IAccessorC0DPacketCloseWindow) packet).getWindowId() == 0) {
                        if (!this.clickQueue.isEmpty()) {
                            this.clickQueue.clear();
                        }
                        if (this.openDelayTicks >= 0) {
                            this.openDelayTicks = -1;
                        }
                        if (this.closeDelayTicks >= 0) {
                            this.closeDelayTicks = -1;
                        }
                    } else {
                        if (!this.clickQueue.isEmpty()) {
                            this.clickQueue.clear();
                        }
                        if (this.openDelayTicks >= 0) {
                            this.openDelayTicks = -1;
                        }
                        if (this.closeDelayTicks >= 0) {
                            this.closeDelayTicks = -1;
                        }
                    }
                }
            } else {
                C0EPacketClickWindow packet = (C0EPacketClickWindow) event.getPacket();
                if (packet.getWindowId() == 0) {
                    if ((packet.getMode() == 3 || packet.getMode() == 4) && packet.getSlotId() == -999) {
                        event.setCancelled(true);
                        return;
                    }
                }
                if (this.pendingStatus != null) {
                    PacketUtil.sendPacketNoEvent(this.pendingStatus);
                    this.pendingStatus = null;
                }
            }
        } else {
            bypass.onPacket(event);
        }
    }

    @Override
    public void onDisabled() {
        bypass.onDisable();
        if (this.keysPressed) {
            if (mc.currentScreen != null) {
                KeyBinding.unPressAllKeys();
            }
            this.keysPressed = false;
        }
        if (this.pendingStatus != null) {
            PacketUtil.sendPacketNoEvent(this.pendingStatus);
            this.pendingStatus = null;
        }
        this.delayTicks = 0;
        while (!this.clickQueue.isEmpty()) {
            PacketUtil.sendPacketNoEvent(this.clickQueue.poll());
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }
}
