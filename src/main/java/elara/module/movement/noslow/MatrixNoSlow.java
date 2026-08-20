package elara.module.movement.noslow;

import elara.event.EventTarget;
import elara.events.SlowDownEvent;
import elara.events.StrafeEvent;
import elara.module.movement.NoSlow;
import elara.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;

public class MatrixNoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;

    public MatrixNoSlow(NoSlow parent) {
        this.parent = parent;
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

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null) {
            Item item = mc.thePlayer.getHeldItem().getItem();
            boolean applies = false;
            if (parent.food.getValue() && item instanceof ItemFood) applies = true;
            if (parent.potion.getValue() && item instanceof ItemPotion) applies = true;
            if (parent.sword.getValue() && item instanceof ItemSword) applies = true;
            if (parent.bow.getValue() && item instanceof ItemBow) applies = true;

            if (applies) {
                if (mc.thePlayer.getItemInUseDuration() > 1) {
                    MoveUtil.setSpeed(0.0265, MoveUtil.getMoveYaw());
                } else {
                    mc.thePlayer.motionX *= 0.992;
                    mc.thePlayer.motionZ *= 0.992;
                }
            }
        }
    }
}
