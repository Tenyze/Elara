package elara.module.movement.noslow;

import elara.event.EventTarget;
import elara.events.MotionEvent;
import elara.events.RightClickMouseEvent;
import elara.events.SlowDownEvent;
import elara.module.movement.NoSlow;
import elara.property.properties.BooleanProperty;
import elara.util.ChatUtil;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;

public class WatchdogNoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;

    private int airTicks;
    private boolean dk;
    private boolean onSlab;
    private Packet<?> NI;

    public final BooleanProperty slowDownOnSlabs;

    public WatchdogNoSlow(NoSlow parent) {
        this.parent = parent;
        this.slowDownOnSlabs = new BooleanProperty("Slow down on Slabs", true, () -> parent.mode.getValue() == 8);
    }

    @EventTarget
    public void onPreMotion(MotionEvent event) {
        if (mc.thePlayer == null) return;

        if (mc.theWorld.getBlockState(new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.motionY, mc.thePlayer.posZ)).getBlock() != Blocks.air
                && !mc.thePlayer.isUsingItem() && this.slowDownOnSlabs.getValue()) {
            this.onSlab = false;
        }

        double posY = mc.thePlayer.posY;
        if (Math.abs(posY - Math.round(posY)) > 0.03 && mc.thePlayer.onGround) {
            this.onSlab = true;
        }

        if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && !(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)) {
            if (mc.thePlayer.onGround) {
                this.airTicks = 0;
            } else {
                this.airTicks++;
            }

            if (this.airTicks >= 2) {
                this.dk = false;
                this.NI = null;
            } else if (mc.thePlayer.onGround && !this.onSlab) {
                mc.thePlayer.posY += 0.001;
            }
        }

        if (this.onSlab && !mc.thePlayer.onGround && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && !(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)) {
            mc.thePlayer.motionX *= 0.1;
            mc.thePlayer.motionZ *= 0.1;
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (mc.thePlayer.getHeldItem() != null) {
            Item item = mc.thePlayer.getHeldItem().getItem();
            if (mc.thePlayer.isUsingItem()
                    || (item instanceof ItemPotion && !ItemPotion.isSplash(mc.thePlayer.getHeldItem().getMetadata()))
                    || item instanceof ItemFood
                    || item instanceof ItemBow) {
                if (mc.thePlayer.getItemInUseDuration() < 2 && mc.thePlayer.getItemInUseDuration() != 0 && !this.onSlab) {
                    ChatUtil.sendRaw("You must start eating while in the air even with potions");
                    event.setCancelled(true);
                } else if (mc.thePlayer.onGround) {
                    mc.thePlayer.jump();
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null) {
            Item item = mc.thePlayer.getHeldItem().getItem();

            if (!this.onSlab || mc.thePlayer.onGround) {
                if (parent.food.getValue() && item instanceof ItemFood) {
                    event.stop();
                }
                if (parent.potion.getValue() && item instanceof ItemPotion
                        && !ItemPotion.isSplash(mc.thePlayer.getHeldItem().getMetadata())) {
                    event.stop();
                }
                if (parent.bow.getValue() && item instanceof ItemBow) {
                    event.stop();
                }
            }

            if (parent.sword.getValue() && item instanceof ItemSword) {
                int currentSlot = mc.thePlayer.inventory.currentItem;
                PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot % 7 + (int) (Math.random() * 2.0) + 1));
                PacketUtil.sendPacket(new C09PacketHeldItemChange(currentSlot));
                event.stop();
            }
        }
    }
}
