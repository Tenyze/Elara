package elara.module.movement;

import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.AttackEvent;
import elara.events.LivingUpdateEvent;
import elara.events.PacketEvent;
import elara.events.TickEvent;
import elara.Elara;
import elara.module.Module;
import elara.module.combat.KillAura;
import elara.module.combat.Knockback;
import elara.property.properties.BooleanProperty;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import elara.property.properties.PercentProperty;
import elara.util.KeyBindUtil;
import elara.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.MovingObjectPosition;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 3, new String[]{"Vanilla", "Legit", "Grim", "Buffer"});

    public final BooleanProperty onHurt = new BooleanProperty("OnHurt", false, () -> mode.getValue() == 1);

    public final PercentProperty slowdown = new PercentProperty("Slowdown", 0, () -> mode.getValue() == 0);
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", false, () -> mode.getValue() == 0);
    public final BooleanProperty reachOnly = new BooleanProperty("Reach Only", false, () -> mode.getValue() == 0);

    public final BooleanProperty autoFactor = new BooleanProperty("Auto Factor", true, () -> mode.getValue() == 2);
    public final PercentProperty offsetBudget = new PercentProperty("Offset Budget", 50, () -> mode.getValue() == 2 && autoFactor.getValue());
    public final PercentProperty factor = new PercentProperty("Factor", 65, () -> mode.getValue() == 2 && !autoFactor.getValue());
    public final BooleanProperty grimGroundOnly = new BooleanProperty("Ground Only", true, () -> mode.getValue() == 2);

    public final PercentProperty bufferSlowdown = new PercentProperty("Buffer Slowdown", 100, () -> mode.getValue() == 3);
    public final IntProperty bufferMaxTicks = new IntProperty("Buffer MaxTicks", 4, 1, 10, () -> mode.getValue() == 3);
    public final BooleanProperty bufferGroundOnly = new BooleanProperty("Ground Only", true, () -> mode.getValue() == 3);

    private int disSprintTicks = 0;
    private final Deque<Packet<?>> pendingSwing = new ConcurrentLinkedDeque<>();
    private final Deque<BufferedAttack> bufferedAttacks = new ConcurrentLinkedDeque<>();
    private int swingTicks = 0;

    public KeepSprint() {
        super("KeepSprint", false);
    }

    private static class BufferedAttack {
        final Packet<?> swing;
        final Packet<?> attack;
        final Entity target;
        int ticks;

        BufferedAttack(Packet<?> swing, Packet<?> attack, Entity target) {
            this.swing = swing;
            this.attack = attack;
            this.target = target;
            this.ticks = 0;
        }
    }

    public boolean shouldKeepSprint() {
        switch (mode.getValue()) {
            case 1:
                return false;
            case 2:
                if (grimGroundOnly.getValue() && !mc.thePlayer.onGround) return false;
                return true;
            case 3:
                if (bufferGroundOnly.getValue() && !mc.thePlayer.onGround) return false;
                return true;
            default:
                if (groundOnly.getValue() && !mc.thePlayer.onGround) return false;
                return !reachOnly.getValue() || mc.objectMouseOver != null
                        && mc.objectMouseOver.hitVec != null
                        && mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F)) > 3.0;
        }
    }

    public boolean isAttackNoSlow() {
        return isEnabled() && (shouldKeepSprint() || mode.getValue() == 3);
    }

    public double getSlowFactor() {
        if (Knockback.blinkActive) return 1.0;
        switch (mode.getValue()) {
            case 1:
                return 0.6;
            case 2:
                if (autoFactor.getValue()) {
                    double speed = Math.hypot(mc.thePlayer.motionX, mc.thePlayer.motionZ);
                    if (speed <= 0.0) return 1.0;
                    double budget = 0.001 * offsetBudget.getValue() / 100.0;
                    double maxFactor = speed * 0.6 < 0.005 ? budget / speed : 0.6 + budget / speed;
                    return Math.min(1.0, maxFactor);
                }
                return factor.getValue().doubleValue() / 100.0;
            case 3:
                if (bufferGroundOnly.getValue() && !mc.thePlayer.onGround) return 0.6;
                return 1.0;
            default:
                return 0.6 + 0.4 * (1.0 - slowdown.getValue().doubleValue() / 100.0);
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.isEnabled() && mode.getValue() == 1) {
            this.disSprintTicks = 3;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && mode.getValue() == 1) {
            if (disSprintTicks >= 0) {
                if (onHurt.getValue() || mc.thePlayer.hurtTime == 0) {
                    KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                    mc.thePlayer.setSprinting(false);
                }
                disSprintTicks--;
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || mode.getValue() != 3) return;
        if (event.getType() != EventType.SEND) return;
        if (bufferGroundOnly.getValue() && !mc.thePlayer.onGround) return;
        if (event.getPacket() instanceof C0APacketAnimation) {
            event.setCancelled(true);
            pendingSwing.offer(event.getPacket());
        } else if (event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity c02 = (C02PacketUseEntity) event.getPacket();
            if (c02.getAction() == C02PacketUseEntity.Action.ATTACK && !pendingSwing.isEmpty()) {
                event.setCancelled(true);
                Entity target = c02.getEntityFromWorld(mc.theWorld);
                bufferedAttacks.offer(new BufferedAttack(pendingSwing.poll(), event.getPacket(), target));
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mode.getValue() != 3) return;
        if (event.getType() != EventType.PRE) return;
        if (bufferedAttacks.isEmpty()) {
            if (!pendingSwing.isEmpty()) {
                if (++swingTicks > 2) {
                    swingTicks = 0;
                    while (!pendingSwing.isEmpty()) {
                        PacketUtil.sendPacketNoEvent(pendingSwing.poll());
                    }
                }
            } else {
                swingTicks = 0;
            }
            return;
        }
        BufferedAttack ba = bufferedAttacks.peek();
        ba.ticks++;
        if (ba.ticks > bufferMaxTicks.getValue()) {
            bufferedAttacks.poll();
            return;
        }
        if (ba.target == null || ba.target.isDead) {
            bufferedAttacks.poll();
            return;
        }
        MovingObjectPosition mop = mc.objectMouseOver;
        KillAura killAura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
        boolean killAuraAiming = killAura != null && killAura.isEnabled()
                && killAura.getTarget() == ba.target && killAura.isAttackAllowed();
        if (!killAuraAiming) {
            if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || mop.entityHit != ba.target) return;
        }
        if (killAura != null && killAura.isEnabled() && killAura.isPlayerBlocking()) return;
        double factor = 0.6 + 0.4 * (1.0 - bufferSlowdown.getValue().doubleValue() / 100.0);
        mc.thePlayer.motionX *= factor;
        mc.thePlayer.motionZ *= factor;
        PacketUtil.sendPacketNoEvent(ba.swing);
        PacketUtil.sendPacketNoEvent(ba.attack);
        bufferedAttacks.poll();
    }

    @Override
    public void onEnabled() {
        disSprintTicks = 0;
        pendingSwing.clear();
        bufferedAttacks.clear();
        swingTicks = 0;
    }

    @Override
    public void onDisabled() {
        while (!pendingSwing.isEmpty()) {
            PacketUtil.sendPacketNoEvent(pendingSwing.poll());
        }
        while (!bufferedAttacks.isEmpty()) {
            BufferedAttack ba = bufferedAttacks.poll();
            PacketUtil.sendPacketNoEvent(ba.swing);
            PacketUtil.sendPacketNoEvent(ba.attack);
        }
        swingTicks = 0;
        if (mode.getValue() == 1) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSprint.getKeyCode());
        }
    }

    @Override
    public String[] getSuffix() {
        switch (mode.getValue()) {
            case 2:
                if (autoFactor.getValue()) {
                    return new String[]{"Grim", String.format("%.0f%%", getSlowFactor() * 100)};
                }
                return new String[]{"Grim", factor.getValue() + "%"};
            case 3:
                return new String[]{"Buffer", bufferMaxTicks.getValue() + "t"};
            default:
                return new String[]{this.mode.getModeString()};
        }
    }
}