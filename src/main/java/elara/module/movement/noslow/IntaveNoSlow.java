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
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class IntaveNoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;
    private boolean usingItem;

    public IntaveNoSlow(NoSlow parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPreMotion(MotionEvent event) {
        if (mc.thePlayer.getCurrentEquippedItem() != null) {
            Item item = mc.thePlayer.getCurrentEquippedItem().getItem();
            if (mc.thePlayer.isUsingItem()) {
                if (item instanceof ItemSword && parent.sword.getValue()) {
                    if (mc.thePlayer.ticksExisted % 5 == 0) {
                        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getCurrentEquippedItem()));
                    }
                } else if ((item instanceof ItemFood && parent.food.getValue())
                        || (item instanceof ItemBow && parent.bow.getValue())) {
                    // PacketQueueComponent bypass omitted (Elara has no packet queue component)
                }
                this.usingItem = true;
            } else if (this.usingItem) {
                this.usingItem = false;
            }
        }
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
}
