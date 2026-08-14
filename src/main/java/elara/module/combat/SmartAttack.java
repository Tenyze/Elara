package elara.module.combat;

import elara.event.EventTarget;
import elara.events.AttackEvent;
import elara.events.LeftClickMouseEvent;
import elara.events.UpdateEvent;
import elara.module.Module;
import elara.module.ModuleCategory;
import elara.property.properties.BooleanProperty;
import elara.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

public class SmartAttack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final BooleanProperty onGround = new BooleanProperty("CancelGroundAttack", true);
    private final BooleanProperty onRising = new BooleanProperty("CancelRisingAttack", true);
    private final IntProperty stopHurtTime = new IntProperty("StopHurtTime", 7, 0, 9);
    public static final BooleanProperty onKillAura = new BooleanProperty("OnKillAura", true);
    public static final BooleanProperty cancelAuraBlocking = new BooleanProperty("CancelAuraBlocking", true, onKillAura::getValue);
    public static boolean shouldCancel;
    private EntityLivingBase target;

    public SmartAttack() {
        super("SmartAttack", false, false, "Smart attack cancellation", ModuleCategory.COMBAT);

        onGround.setCategory("Conditions");
        onRising.setCategory("Conditions");
        stopHurtTime.setCategory("Timing");
        onKillAura.setCategory("Conditions");
        cancelAuraBlocking.setCategory("Combat");
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (isEnabled()) {
            if (event.getTarget() instanceof EntityLivingBase) {
                target = (EntityLivingBase) event.getTarget();
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (isEnabled()) {
            if (target != null && mc.thePlayer.getDistanceToEntity(target) > 6) {
                target = null;
            }
            if (target == null) {
                shouldCancel = false;
                return;
            }
            if (mc.thePlayer.onGround && onGround.getValue()) {
                shouldCancel = true;
            }
            if (mc.thePlayer.motionY >= 0 && onRising.getValue()) {
                shouldCancel = true;
            }
            if (target.hurtTime <= 2) {
                shouldCancel = false;
            }
            if (target.isBurning()) {
                shouldCancel = false;
            }
            if (mc.thePlayer.hurtTime > stopHurtTime.getValue()) {
                shouldCancel = false;
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (shouldCancel) {
            event.setCancelled(true);
        }
    }
}
