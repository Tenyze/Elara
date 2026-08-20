package elara.module.movement.noslow;

import elara.event.EventTarget;
import elara.events.SlowDownEvent;
import elara.module.movement.NoSlow;
import elara.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;

public class PredictionNoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;

    public final IntProperty amount;

    public PredictionNoSlow(NoSlow parent) {
        this.parent = parent;
        this.amount = new IntProperty("Amount", 2, 2, 5, () -> parent.mode.getValue() == 7);
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (mc.thePlayer.getItemInUseDuration() % this.amount.getValue() != 0 && mc.thePlayer.onGround) {
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
}
