package elara.module.movement.noslow;

import elara.event.EventTarget;
import elara.events.SlowDownEvent;
import elara.module.movement.NoSlow;
import elara.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;

public class VariableNoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;

    public final FloatProperty multiplier;

    public VariableNoSlow(NoSlow parent) {
        this.parent = parent;
        this.multiplier = new FloatProperty("Multiplier", 0.8F, 0.2F, 1.0F, () -> parent.mode.getValue() == 6);
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null) {
            Item item = mc.thePlayer.getHeldItem().getItem();
            float mult = this.multiplier.getValue();
            if (parent.food.getValue() && item instanceof ItemFood) {
                event.setForwardMultiplier(mult);
                event.setStrafeMultiplier(mult);
            }
            if (parent.potion.getValue() && item instanceof ItemPotion) {
                event.setForwardMultiplier(mult);
                event.setStrafeMultiplier(mult);
            }
            if (parent.sword.getValue() && item instanceof ItemSword) {
                event.setForwardMultiplier(mult);
                event.setStrafeMultiplier(mult);
            }
            if (parent.bow.getValue() && item instanceof ItemBow) {
                event.setForwardMultiplier(mult);
                event.setStrafeMultiplier(mult);
            }
        }
    }
}
