package elara.module.combat;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.event.types.Priority;
import elara.events.PacketEvent;
import elara.events.UpdateEvent;
import elara.module.Module;
import elara.property.properties.BooleanProperty;
import elara.property.properties.FloatProperty;
import elara.property.properties.ModeProperty;
import elara.util.ItemUtil;
import elara.util.RandomUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.Vec3;

public class HitSelect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SECOND", "CRITICALS", "W_TAP"});
    public final FloatProperty range = new FloatProperty("Range", 3.0f, 2.5f, 4.0f);
    public final FloatProperty chance = new FloatProperty("Chance", 80.0f, 0.0f, 100.0f);
    public final BooleanProperty onlySword = new BooleanProperty("Only Sword", true);

    private boolean sprintState = false;
    private int delayTimer = 0;
    private int attackTimer = 0;

    public HitSelect() {
        super("HitSelect", false);
    }

    private Entity getTarget() {
        KillAura ka = (KillAura) Elara.moduleManager.getModule(KillAura.class);
        if (ka != null && ka.isEnabled() && ka.target != null) {
            return ka.target.getEntity();
        }
        if (mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
            return mc.objectMouseOver.entityHit;
        }
        return null;
    }

    private void doAttack(Entity target) {
        if (target == null || mc.thePlayer == null) return;
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, target);
        resetState();
    }

    private void resetState() {
        delayTimer = 0;
        attackTimer = 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() != EventType.PRE) return;

        if (onlySword.getValue() && !ItemUtil.isHoldingSword()) {
            resetState();
            return;
        }

        Entity target = getTarget();
        if (target == null) {
            resetState();
            return;
        }

        double dist = mc.thePlayer.getDistanceToEntity(target);
        int modeVal = mode.getValue();

        if (modeVal == 0) {
            if (attackTimer > 0) {
                attackTimer--;
                if (attackTimer == 0 && dist <= range.getValue()) {
                    doAttack(target);
                }
            } else if (dist <= range.getValue() && RandomUtil.nextFloat(0, 100) < chance.getValue()) {
                attackTimer = RandomUtil.nextInt(1, 4);
            }
        } else if (modeVal == 1) {
            if (mc.thePlayer.hurtTime > 0 && dist <= range.getValue()) {
                delayTimer = 2;
            }
            if (delayTimer > 0) {
                delayTimer--;
                if (delayTimer == 0 && dist <= range.getValue()) {
                    doAttack(target);
                }
            }
        } else if (modeVal == 2) {
            if (dist >= 2.8 && dist <= range.getValue() && RandomUtil.nextFloat(0, 100) < chance.getValue()) {
                doAttack(target);
            }
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) return;
        if (event.getPacket() instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction packet = (C0BPacketEntityAction) event.getPacket();
            if (packet.getAction() == C0BPacketEntityAction.Action.START_SPRINTING) {
                sprintState = true;
            } else if (packet.getAction() == C0BPacketEntityAction.Action.STOP_SPRINTING) {
                sprintState = false;
            }
        }
    }

    @Override
    public void onDisabled() {
        resetState();
        sprintState = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }
}