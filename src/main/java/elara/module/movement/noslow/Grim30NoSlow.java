package elara.module.movement.noslow;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.PacketEvent;
import elara.events.PlayerUpdateEvent;
import elara.events.RightClickMouseEvent;
import elara.events.SlowDownEvent;
import elara.mixin.IAccessorEntity;
import elara.module.movement.NoSlow;
import elara.property.properties.BooleanProperty;
import elara.util.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;

public class Grim30NoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;

    private int usingItemTicks;

    public final BooleanProperty heypixel;

    public Grim30NoSlow(NoSlow parent) {
        this.parent = parent;
        this.heypixel = new BooleanProperty("Heypixel", false, () -> parent.mode.getValue() == 11);
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.thePlayer.isUsingItem()
                && !mc.thePlayer.onGround
                && !mc.gameSettings.keyBindRight.isKeyDown()
                && !mc.gameSettings.keyBindLeft.isKeyDown()) {
            Elara.rotationManager.setRotation(mc.thePlayer.rotationYaw + 45.0F, mc.thePlayer.rotationPitch, 10, true);
        }

        if (((IAccessorEntity) mc.thePlayer).getIsInWeb()) {
            MoveUtil.setSpeed(0.64, MoveUtil.getMoveYaw());
        }

        if (mc.thePlayer.isUsingItem() && mc.thePlayer.getItemInUseDuration() > 1
                && !mc.gameSettings.keyBindJump.isKeyDown()) {
            MoveUtil.addSpeed(2.0E-4, MoveUtil.getMoveYaw());

            if (!mc.gameSettings.keyBindRight.isKeyDown()
                    && !mc.gameSettings.keyBindLeft.isKeyDown()
                    && (mc.thePlayer.getHeldItem() == null || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow))) {
                Elara.rotationManager.setRotation(mc.thePlayer.rotationYaw + 45.0F, mc.thePlayer.rotationPitch, 10, true);
            }
        }

        if (mc.thePlayer.isUsingItem() && mc.thePlayer.moveForward > 0.0F) {
            mc.thePlayer.setSprinting(true);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND) return;
        if (this.heypixel.getValue() && event.getPacket() instanceof C0FPacketConfirmTransaction) {
            if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null) {
                Item item = mc.thePlayer.getHeldItem().getItem();
                if (item instanceof ItemFood || item instanceof ItemPotion || item instanceof ItemBow) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (mc.thePlayer.getItemInUseDuration() % 2 == 1 && !mc.thePlayer.onGround) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (mc.thePlayer.isUsingItem()) {
            this.usingItemTicks++;
            MoveUtil.addSpeed(1.0E-4, MoveUtil.getMoveYaw());
        } else {
            this.usingItemTicks = 0;
        }

        if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null) {
            Item item = mc.thePlayer.getHeldItem().getItem();
            int duration = mc.thePlayer.getItemInUseDuration();

            if (duration == 1
                    || (duration % 2 == 0 && !mc.thePlayer.onGround)
                    || (duration % 2 == 1 && mc.thePlayer.onGround)) {
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
