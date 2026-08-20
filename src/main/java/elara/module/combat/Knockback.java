package elara.module.combat;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.event.types.Priority;
import elara.events.*;
import elara.mixin.IAccessorEntity;
import elara.module.Module;
import elara.property.properties.*;
import elara.util.ItemUtil;
import elara.util.MoveUtil;
import elara.util.PacketUtil;
import elara.util.RandomUtil;
import elara.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.Potion;
import net.minecraft.util.MovingObjectPosition;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Knockback extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random rand = new Random();

    @Deprecated
    public static boolean blinkActive = false;

    // ==================== 通用模式 Reduce/Jump/Hybrid/Delay ====================
    public final ModeProperty mode = new ModeProperty("Mode", 2, new String[]{"Reduce", "Jump", "Hybrid", "Delay"});

    // ---- Reduce/Hybrid ----
    public final IntProperty wtapDuration = new IntProperty("W-tap(ms)", 90, 20, 200);
    public final IntProperty resetDelay = new IntProperty("Reset Delay", 30, 0, 100);
    public final IntProperty chance = new IntProperty("Chance", 92, 0, 100);
    public final BooleanProperty onlySprint = new BooleanProperty("Only Sprint", true);
    public final BooleanProperty requireSword = new BooleanProperty("Require Sword", true);
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", true);
    public final BooleanProperty debug = new BooleanProperty("Debug", false);

    // ---- Jump/Hybrid ----
    public final FloatProperty jumpStrength = new FloatProperty("Jump", 0.85f, 0.0f, 1.0f);

    // ---- Delay ----
    public final IntProperty airDelay = new IntProperty("AirDelay", 90, 0, 1000);
    public final IntProperty groundDelay = new IntProperty("GroundDelay", 0, 0, 1000);
    public final IntProperty delayChance = new IntProperty("Delay Chance", 100, 0, 100);
    public final BooleanProperty realtimeDamage = new BooleanProperty("RealtimeDamage", true);
    public final BooleanProperty requireTarget = new BooleanProperty("RequireTarget", false);
    public final BooleanProperty onlySwords = new BooleanProperty("OnlySwords", false);

    // ---------------------- Reduce/Jump/Hybrid 运行状态 ----------------------
    private long lastAttackAt = -1L;
    private int wtapTicks = 0;
    private int sprintResetTicks = 0;
    private int hurtTimeLatch = -1;
    private boolean shortJumpQueued = false;
    private final HashMap<Integer, Float> prevEnemySwings = new HashMap<>();

    // ---------------------- Delay 运行状态 ----------------------
    private final Queue<TimedPacket> packets = new ConcurrentLinkedQueue<>();
    private boolean blink;

    public Knockback() {
        super("Knockback", false, false);

        // 分类
        wtapDuration.setCategory("Reduce");
        resetDelay.setCategory("Reduce");
        chance.setCategory("Reduce");
        onlySprint.setCategory("Reduce");
        requireSword.setCategory("Reduce");
        groundOnly.setCategory("Reduce");
        jumpStrength.setCategory("Jump");
        airDelay.setCategory("Delay");
        groundDelay.setCategory("Delay");
        delayChance.setCategory("Delay");
        realtimeDamage.setCategory("Delay");
        requireTarget.setCategory("Delay");
        onlySwords.setCategory("Delay");
    }

    // ==================================================================
    //  Reduce/Jump/Hybrid 逻辑（原 Knockback）
    // ==================================================================

    private boolean canReduceAct() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb()) return false;
        if (requireSword.getValue() && !ItemUtil.isHoldingSword()) return false;
        if (onlySprint.getValue() && !mc.thePlayer.isSprinting() && MoveUtil.getForwardValue() <= 0) return false;
        if (mc.thePlayer.isPotionActive(Potion.jump)) return false;
        return true;
    }

    private boolean canGroundAct() {
        if (!canReduceAct()) return false;
        if (groundOnly.getValue() && !mc.thePlayer.onGround) return false;
        return true;
    }

    private boolean shouldTrigger() {
        int c = chance.getValue();
        if (c >= 100) return true;
        if (c <= 0) return false;
        return rand.nextInt(100) < c;
    }

    private boolean recentlyAttacked() {
        return lastAttackAt > 0 && (System.currentTimeMillis() - lastAttackAt) < 350L;
    }

    private boolean anyEnemyAboutToHitMe() {
        if (mc.theWorld == null) return false;
        List<Entity> ents = new ArrayList<>(mc.theWorld.loadedEntityList);
        HashSet<Integer> visibleIds = new HashSet<>();
        boolean anyTrigger = false;

        for (Entity e : ents) {
            if (!(e instanceof EntityLivingBase)) continue;
            if (e == mc.thePlayer) continue;
            EntityLivingBase living = (EntityLivingBase) e;
            if (living.deathTime > 0) continue;

            int id = e.getEntityId();
            visibleIds.add(id);
            double dist = RotationUtil.distanceToEntity(living);
            if (dist > 4.0) {
                prevEnemySwings.put(id, living.swingProgress);
                continue;
            }

            float prev = prevEnemySwings.containsKey(id) ? prevEnemySwings.get(id) : 0.0f;
            float now = living.swingProgress;
            prevEnemySwings.put(id, now);

            boolean hostile = (living instanceof EntityPlayer);
            if (!hostile) {
                net.minecraft.item.ItemStack held = living.getHeldItem();
                hostile = held != null && held.getItem() instanceof ItemSword;
            }
            if (!hostile) continue;

            float[] toMe = RotationUtil.getRotationsTo(
                    mc.thePlayer.posX,
                    mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                    mc.thePlayer.posZ,
                    living.rotationYaw,
                    living.rotationPitch);
            float yawDiff = Math.abs(RotationUtil.normalizeAngle(toMe[0] - living.rotationYaw));
            if (yawDiff > 90.0f) continue;

            if (prev <= 0.04f && now > 0.05f) { anyTrigger = true; continue; }
            if (now >= 0.12f && now <= 0.92f) { anyTrigger = true; continue; }
            if (dist <= 3.0 && living.hurtResistantTime >= 8) { anyTrigger = true; }
        }
        prevEnemySwings.keySet().retainAll(visibleIds);
        return anyTrigger;
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacketSend(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) return;
        if (mode.getValue() == 3) return; // Delay 模式用接收端逻辑，不处理发送端

        if (!(event.getPacket() instanceof C02PacketUseEntity)) return;
        C02PacketUseEntity p = (C02PacketUseEntity) event.getPacket();
        if (p.getAction() != C02PacketUseEntity.Action.ATTACK) return;
        Entity tgt = p.getEntityFromWorld(mc.theWorld);
        if (!(tgt instanceof EntityPlayer) || tgt == mc.thePlayer) return;
        lastAttackAt = System.currentTimeMillis();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!isEnabled()) return;
        if (mode.getValue() == 3) return;
        if (event.getTarget() instanceof EntityPlayer && event.getTarget() != mc.thePlayer) {
            lastAttackAt = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled()) return;
        if (mode.getValue() == 3) {
            onTickDelayMode(event);
        } else {
            onTickNormalMode(event);
        }
    }

    private void onTickNormalMode(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null) return;

        if (wtapTicks > 0) {
            wtapTicks--;
        }
        if (sprintResetTicks > 0) {
            sprintResetTicks--;
        }

        if (shortJumpQueued) {
            if (mc.thePlayer.onGround && !mc.thePlayer.isInWater() && !mc.thePlayer.isOnLadder()) {
                mc.thePlayer.jump();
                shortJumpQueued = false;
            } else if (sprintResetTicks == 0 && wtapTicks == 0) {
                shortJumpQueued = false;
            }
        }

        int m = mode.getValue();

        boolean incoming = anyEnemyAboutToHitMe();
        if (incoming && canReduceAct() && shouldTrigger()) {
            if ((m == 0 || m == 2) && sprintResetTicks == 0 && mc.thePlayer.isSprinting()) {
                mc.thePlayer.setSprinting(false);
                sprintResetTicks = 3;
            }
            if ((m == 0 || m == 2) && wtapTicks == 0 && MoveUtil.getForwardValue() > 0) {
                wtapTicks = Math.max(1, Math.round(wtapDuration.getValue() / 50.0f));
                mc.thePlayer.movementInput.moveForward = 0f;
            }
        }

        if (recentlyAttacked() && canReduceAct() && shouldTrigger()) {
            long dt = System.currentTimeMillis() - lastAttackAt;
            if ((m == 0 || m == 2) && dt >= resetDelay.getValue() && sprintResetTicks == 0 && mc.thePlayer.isSprinting()) {
                mc.thePlayer.setSprinting(false);
                sprintResetTicks = 3;
            }
            if ((m == 0 || m == 2) && wtapTicks == 0 && MoveUtil.getForwardValue() > 0) {
                wtapTicks = Math.max(1, Math.round(wtapDuration.getValue() / 50.0f));
                mc.thePlayer.movementInput.moveForward = 0f;
            }
        }

        int ht = mc.thePlayer.hurtTime;
        if (hurtTimeLatch != ht && ht >= 9 && canGroundAct() && shouldTrigger()) {
            if ((m == 1 || m == 2) && mc.thePlayer.onGround) {
                if (jumpStrength.getValue() >= 0.999f || rand.nextFloat() <= jumpStrength.getValue()) {
                    shortJumpQueued = true;
                }
            }
        }
        hurtTimeLatch = ht;
    }

    @EventTarget(Priority.HIGHEST)
    public void onMove(MoveInputEvent event) {
        if (!isEnabled() || mode.getValue() == 3) return;
        if (wtapTicks <= 0) return;
        if (mc.gameSettings.keyBindForward.isKeyDown() || MoveUtil.getForwardValue() > 0) {
            mc.thePlayer.movementInput.moveForward = 0f;
        }
    }

    // ==================================================================
    //  Delay 模式（原 KnockbackDelay）
    // ==================================================================

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (isEnabled() && mode.getValue() == 3) {
            resetDelayState();
        }
    }

    private void onTickDelayMode(TickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.isSingleplayer() || mc.thePlayer.ticksExisted < 20) return;

        if (mc.currentScreen != null) {
            resetDelayState();
            return;
        }

        if (!shouldDelayActivate()) {
            resetDelayState();
            return;
        }

        int delay = mc.thePlayer.onGround ? groundDelay.getValue() : airDelay.getValue();

        if (!packets.isEmpty()) {
            handleDelayPackets(delay);
        }

        if (mc.thePlayer.hurtTime > 0) {
            blink = true;
        } else if (packets.isEmpty()) {
            blink = false;
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacketReceive(PacketEvent event) {
        if (!isEnabled() || event.getType() != EventType.RECEIVE) return;
        if (mode.getValue() != 3) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (mc.isSingleplayer() || mc.thePlayer.ticksExisted < 20 || event.isCancelled()) return;

        Packet<?> packet = event.getPacket();
        if (PacketUtil.isWorldRenderPacket(packet)) return;

        if (packet instanceof S07PacketRespawn) {
            resetDelayState();
            return;
        }

        // Let damage status through in realtime so hurt animation plays immediately
        if (realtimeDamage.getValue() && packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus statusPacket = (S19PacketEntityStatus) packet;
            if (statusPacket.getOpCode() == 2 && statusPacket.getEntity(mc.theWorld) == mc.thePlayer) {
                return;
            }
        }

        if (!blink && isPlayerKnockbackPacket(packet) && shouldDelayActivate()) {
            blink = true;
        }

        if (blink) {
            event.setCancelled(true);
            packets.add(new TimedPacket(packet, System.currentTimeMillis()));
        }
    }

    private boolean isPlayerKnockbackPacket(Packet<?> packet) {
        if (packet instanceof S12PacketEntityVelocity) {
            return ((S12PacketEntityVelocity) packet).getEntityID() == mc.thePlayer.getEntityId();
        }
        if (packet instanceof S27PacketExplosion) {
            S27PacketExplosion explosion = (S27PacketExplosion) packet;
            return explosion.func_149149_c() != 0.0F || explosion.func_149144_d() != 0.0F || explosion.func_149147_e() != 0.0F;
        }
        return false;
    }

    private boolean shouldDelayActivate() {
        if (RandomUtil.nextInt(0, 100) > delayChance.getValue()) return false;
        if (requireTarget.getValue() && findTarget() == null) return false;
        if (onlySwords.getValue() && !ItemUtil.isHoldingSword()) return false;
        return true;
    }

    private void resetDelayState() {
        if (!blink) {
            // 即使没 blink，也确保队列清空避免残留
            if (!packets.isEmpty()) flushDelayPackets();
            return;
        }
        blink = false;
        flushDelayPackets();
    }

    private void handleDelayPackets(int delay) {
        while (!packets.isEmpty()) {
            TimedPacket wrapper = packets.peek();
            if (wrapper != null && wrapper.elapsed(delay)) {
                packets.poll();
                processPacketSilent(wrapper.packet);
            } else {
                break;
            }
        }
    }

    private void flushDelayPackets() {
        TimedPacket wrapper;
        while ((wrapper = packets.poll()) != null) {
            processPacketSilent(wrapper.packet);
        }
    }

    @SuppressWarnings("unchecked")
    private void processPacketSilent(Packet<?> packet) {
        try {
            if (mc.getNetHandler() != null) {
                ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class TimedPacket {
        private final Packet<?> packet;
        private final long time;

        public TimedPacket(Packet<?> packet, long time) {
            this.packet = packet;
            this.time = time;
        }

        public boolean elapsed(int delayMs) {
            return System.currentTimeMillis() - time >= delayMs;
        }
    }

    private Entity findTarget() {
        KillAura ka = (KillAura) Elara.moduleManager.getModule(KillAura.class);
        if (ka != null && ka.isEnabled() && ka.getTarget() != null) {
            return ka.getTarget();
        }
        if (mc.pointedEntity != null) return mc.pointedEntity;
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            return mc.objectMouseOver.entityHit;
        }
        return null;
    }

    // ==================================================================
    //  生命周期
    // ==================================================================

    @Override
    public void onEnabled() {
        lastAttackAt = -1L;
        wtapTicks = 0;
        sprintResetTicks = 0;
        hurtTimeLatch = -1;
        shortJumpQueued = false;
        prevEnemySwings.clear();
        blink = false;
        packets.clear();
    }

    @Override
    public void onDisabled() {
        // 恢复正常模式的 moveForward
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) {
            if (wtapTicks > 0 && mc.gameSettings.keyBindForward.isKeyDown()) {
                mc.thePlayer.movementInput.moveForward = 1f;
            }
        }
        resetDelayState();
        onEnabled();
    }

    @Override
    public String[] getSuffix() {
        if (mode.getValue() == 3) {
            return new String[]{"Delay " + airDelay.getValue() + "-" + groundDelay.getValue()};
        }
        return new String[]{mode.getModeString() + " " + chance.getValue() + "%"};
    }
}
