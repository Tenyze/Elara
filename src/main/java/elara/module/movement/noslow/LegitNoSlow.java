package elara.module.movement.noslow;

import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.MotionEvent;
import elara.events.PacketEvent;
import elara.events.SlowDownEvent;
import elara.mixin.IAccessorKeyBinding;
import elara.module.movement.NoSlow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S29PacketSoundEffect;

public class LegitNoSlow {
    private static final int MAX_USE_TICKS = 32;
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;

    private int useTicks;
    private boolean heardBurp;
    private boolean slowDownStarted;
    private boolean delaying;
    private int delayTicks;
    private int lastSlowTick = -1;

    public LegitNoSlow(NoSlow parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPreMotion(MotionEvent event) {
        if (mc.thePlayer != null && mc.thePlayer.getHeldItem() != null) {
            if (this.shouldStopUsing()) {
                mc.thePlayer.stopUsingItem();
                ((IAccessorKeyBinding) mc.gameSettings.keyBindUseItem).setPressed(false);
            }
        }
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        int tick = mc.thePlayer.ticksExisted;
        if (tick == this.lastSlowTick) return;
        this.lastSlowTick = tick;

        if (!this.isUsingBypassedItem()) {
            this.resetDelay();
        } else {
            boolean flag = mc.thePlayer.getItemInUseDuration() % 3 != 0;
            if (!flag) {
                this.resetDelay();
            } else if (!this.slowDownStarted) {
                this.delaying = true;
                this.delayTicks = 0;
                this.slowDownStarted = true;
            } else if (this.delaying) {
                this.delayTicks--;
                if (this.delayTicks <= 0) {
                    this.delaying = false;
                    mc.thePlayer.setSprinting(true);
                }
            } else {
                mc.thePlayer.setSprinting(true);
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE) return;
        if (event.getPacket() instanceof S29PacketSoundEffect) {
            S29PacketSoundEffect s29 = (S29PacketSoundEffect) event.getPacket();
            if (s29.getSoundName() != null && s29.getSoundName().contains("random.burp")) {
                this.heardBurp = true;
            }
        }
    }

    public void onDisable() {
        this.resetDelay();
        ((IAccessorKeyBinding) mc.gameSettings.keyBindUseItem).setPressed(GameSettings.isKeyDown(mc.gameSettings.keyBindUseItem));
    }

    private boolean shouldStopUsing() {
        if (!mc.thePlayer.isUsingItem()) {
            return false;
        }
        ItemStack itemstack = mc.thePlayer.getHeldItem();
        if (itemstack == null) {
            return false;
        }
        Item item = itemstack.getItem();
        return mc.thePlayer.getItemInUseDuration() > 12 && !(item instanceof ItemBow) && this.appliesTo(itemstack, false);
    }

    private boolean isUsingBypassedItem() {
        if (!mc.thePlayer.isUsingItem()) {
            return false;
        }
        ItemStack itemstack = mc.thePlayer.getHeldItem();
        return itemstack != null && this.appliesTo(itemstack, true);
    }

    private boolean appliesTo(ItemStack stack, boolean var2) {
        Item item = stack.getItem();
        if (item instanceof ItemFood) {
            return parent.food.getValue();
        } else if (item instanceof ItemPotion) {
            return parent.potion.getValue() && !ItemPotion.isSplash(stack.getMetadata());
        }
        return item instanceof ItemSword ? parent.sword.getValue() : var2 && item instanceof ItemBow && parent.bow.getValue();
    }

    private void resetDelay() {
        this.slowDownStarted = false;
        this.delaying = false;
        this.delayTicks = 0;
    }
}
