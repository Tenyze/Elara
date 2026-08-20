package elara.module.movement.noslow;

import elara.Elara;
import elara.event.EventTarget;
import elara.events.MotionEvent;
import elara.events.SlowDownEvent;
import elara.module.combat.KillAura;
import elara.module.movement.NoSlow;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;

public class NewNCPNoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;
    private int disable;

    public NewNCPNoSlow(NoSlow parent) {
        this.parent = parent;
    }

    @EventTarget
    public void onPreMotion(MotionEvent event) {
        this.disable++;
        this.handleFood();
        this.handlePotion();
        this.handleSword();
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (getKillAuraTarget() != null) return;
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

    private void handleFood() {
        if (parent.food.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemFood) {
            this.sendPlacement();
        }
    }

    private void handlePotion() {
        if (parent.potion.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemPotion) {
            this.sendPlacement();
        }
    }

    private void handleSword() {
        if (parent.sword.getValue() && mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword) {
            this.sendPlacement();
        }
    }

    private void sendPlacement() {
        if (this.disable > 10 && getKillAuraTarget() == null) {
            PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 5, null, 0.0F, 0.0F, 0.0F));
        }
    }

    private Object getKillAuraTarget() {
        try {
            KillAura killAura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
            return killAura.target;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
