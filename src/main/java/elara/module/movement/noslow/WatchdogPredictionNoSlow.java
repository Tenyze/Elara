package elara.module.movement.noslow;

import elara.Elara;
import elara.enums.BlinkModules;
import elara.event.EventTarget;
import elara.events.MotionEvent;
import elara.events.PlayerUpdateEvent;
import elara.events.SlowDownEvent;
import elara.mixin.IAccessorKeyBinding;
import elara.module.combat.KillAura;
import elara.module.movement.NoSlow;
import elara.property.properties.BooleanProperty;
import elara.property.properties.IntProperty;
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

public class WatchdogPredictionNoSlow {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final NoSlow parent;

    private boolean wasUsingItem;
    private int usingTicks;

    public final IntProperty maxPingSpoof;
    public final IntProperty whenToFinishEating;
    public final BooleanProperty nonBlinkSpeedBypass;

    public WatchdogPredictionNoSlow(NoSlow parent) {
        this.parent = parent;
        this.maxPingSpoof = new IntProperty("Max Ping Spoof", 8, 0, 30, () -> parent.mode.getValue() == 5);
        this.whenToFinishEating = new IntProperty("When to finish eating", 30, 20, 36, () -> parent.mode.getValue() == 5);
        this.nonBlinkSpeedBypass = new BooleanProperty("Non-Blink Speed Bypass", true, () -> parent.mode.getValue() == 5);
    }

    @EventTarget
    public void onPreMotion(MotionEvent event) {
        if (mc.thePlayer.getCurrentEquippedItem() == null) return;

        Item item = mc.thePlayer.getCurrentEquippedItem().getItem();
        if (mc.thePlayer.isUsingItem()) {
            if ((!(item instanceof ItemSword) || !parent.sword.getValue())
                    && (parent.food.getValue() && item instanceof ItemFood && mc.thePlayer.isUsingItem()
                            || parent.bow.getValue() && item instanceof ItemBow
                            || parent.potion.getValue() && item instanceof ItemPotion
                                    && !ItemPotion.isSplash(mc.thePlayer.getHeldItem().getMetadata())
                                    && mc.thePlayer.isUsingItem())) {
                this.usingTicks++;
                if (this.usingTicks > this.maxPingSpoof.getValue()) {
                    Elara.blinkManager.setBlinkState(true, BlinkModules.NO_SLOW);
                }
            }

            if (mc.thePlayer.isUsingItem() && mc.thePlayer.moveForward > 0.0F
                    && this.nonBlinkSpeedBypass.getValue() && this.usingTicks <= this.maxPingSpoof.getValue()) {
                mc.thePlayer.setSprinting(true);
            }

            this.wasUsingItem = true;
        } else if (this.wasUsingItem) {
            this.usingTicks = 0;
            this.wasUsingItem = false;
            Elara.blinkManager.setBlinkState(false, BlinkModules.NO_SLOW);
        }

        if (this.usingTicks > this.whenToFinishEating.getValue()) {
            ((IAccessorKeyBinding) mc.gameSettings.keyBindUseItem).setPressed(false);
        }
    }

    @EventTarget
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if ((!isKillAuraActive())
                && (!mc.thePlayer.onGround || mc.thePlayer.getItemInUseDuration() > 2)
                && !mc.gameSettings.keyBindRight.isKeyDown()
                && !mc.gameSettings.keyBindLeft.isKeyDown()
                && mc.thePlayer.ticksExisted > 5
                && mc.thePlayer.isUsingItem()
                && (mc.thePlayer.getHeldItem() == null || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemSword))
                && (mc.thePlayer.getHeldItem() == null || !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow))) {
            Elara.rotationManager.setRotation(mc.thePlayer.rotationYaw + 45.0F, mc.thePlayer.rotationPitch, 10, true);
        }
    }

    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (mc.thePlayer.isUsingItem() && mc.thePlayer.getHeldItem() != null) {
            Item item = mc.thePlayer.getHeldItem().getItem();

            if (parent.food.getValue() && item instanceof ItemFood && this.usingTicks > this.maxPingSpoof.getValue()) {
                event.stop();
            }
            if (parent.potion.getValue() && item instanceof ItemPotion && this.usingTicks > this.maxPingSpoof.getValue()) {
                event.stop();
            }
            if (parent.sword.getValue() && item instanceof ItemSword) {
                PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                event.stop();
            }
            if (parent.bow.getValue() && item instanceof ItemBow && this.usingTicks > this.maxPingSpoof.getValue()) {
                event.stop();
            }
        }
    }

    private boolean isKillAuraActive() {
        try {
            KillAura killAura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
            return killAura.isEnabled() && killAura.target != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
