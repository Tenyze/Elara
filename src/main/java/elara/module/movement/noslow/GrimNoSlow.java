package elara.module.movement.noslow;

import elara.event.EventTarget;
import elara.events.MotionEvent;
import elara.events.SlowDownEvent;
import elara.module.movement.NoSlow;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class GrimNoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;

    public GrimNoSlow(NoSlow parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPreMotion(MotionEvent event) {
        this.applyFood();
        this.applyPotion();
        this.applySword();
        this.applyBow();
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null) {
            Item item = mc.thePlayer.getHeldItem().getItem();
            if (parent.food.getValue() && item instanceof ItemFood) {
                event.stop();
            }
            if (parent.potion.getValue() && item instanceof ItemPotion) {
                event.stop();
            }
            if (parent.sword.getValue() && item instanceof ItemSword) {
                event.stop();
            }
            if (parent.bow.getValue() && item instanceof ItemBow) {
                event.stop();
            }
        }
    }

    private void applyFood() {
        if (parent.food.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemFood) {
            this.sendSlotSwap();
        }
    }

    private void applyPotion() {
        if (parent.potion.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion) {
            this.sendSlotSwap();
        }
    }

    private void applySword() {
        if (parent.sword.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
            this.sendSlotSwap();
        }
    }

    private void applyBow() {
        if (parent.bow.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow) {
            this.sendSlotSwap();
        }
    }

    private void sendSlotSwap() {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot % 8 + 1));
        PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot));
    }
}
