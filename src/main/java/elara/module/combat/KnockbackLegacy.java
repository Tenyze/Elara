package elara.module.combat;

import com.google.common.base.CaseFormat;
import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.AttackEvent;
import elara.events.JumpEvent;
import elara.events.PacketEvent;
import elara.events.StrafeEvent;
import elara.events.TickEvent;
import elara.events.UpdateEvent;
import elara.mixin.IAccessorEntity;
import elara.mixin.IAccessorEntityLivingBase;
import elara.mixin.IAccessorEntityPlayer;
import elara.mixin.IAccessorKeyBinding;
import elara.mixin.IAccessorMinecraft;
import elara.mixin.IAccessorS12PacketEntityVelocity;
import elara.mixin.IAccessorS27PacketExplosion;
import elara.mixin.IAccessorTimer;
import elara.module.Module;
import elara.property.properties.BooleanProperty;
import elara.property.properties.FloatProperty;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import elara.util.BadPacketUtil;
import elara.util.MoveUtil;
import elara.util.PacketUtil;
import elara.util.RandomUtil;
import elara.util.RayCastUtil;
import elara.util.RotationUtil;
import elara.util.TimerUtil;
import elara.util.rotation.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C0BPacketEntityAction.Action;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.potion.Potion;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;

public class KnockbackLegacy extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{
            "Simple", "AAC", "Reverse", "Jump", "Glitch", "Legit",
            "Vulcan", "Matrix", "Intave", "Grim", "Hypixel",
            "PolarBlock", "JumpReset", "Prediction", "Vanilla",
            "Predict", "Reduce", "Delay", "Polar", "GrimReduce"
    });

    public final FloatProperty horizontal = new FloatProperty("Horizontal", 0.0F, -2.0F, 2.0F,
            () -> mode.getValue() == 0 || mode.getValue() == 5);
    public final FloatProperty vertical = new FloatProperty("Vertical", 0.0F, -2.0F, 2.0F,
            () -> mode.getValue() == 0 || mode.getValue() == 5);
    public final IntProperty predictionChance = new IntProperty("PredChance", 100, 0, 100,
            () -> mode.getValue() == 13);
    public final FloatProperty predictionHorizontal = new FloatProperty("PredHorizontal", 0.0F, 0.0F, 1.0F,
            () -> mode.getValue() == 13);
    public final FloatProperty predictionVertical = new FloatProperty("PredVertical", 1.0F, 0.0F, 1.0F,
            () -> mode.getValue() == 13);
    public final BooleanProperty predictionFakeCheck = new BooleanProperty("PredFakeCheck", false,
            () -> mode.getValue() == 13);
    public final BooleanProperty predictionDebug = new BooleanProperty("PredDebug", false,
            () -> mode.getValue() == 13);
    public final FloatProperty reverseStrength = new FloatProperty("ReverseStrength", 1.0F, 0.1F, 1.0F,
            () -> mode.getValue() == 2);
    public final BooleanProperty onLook = new BooleanProperty("OnLook", false,
            () -> mode.getValue() == 2);
    public final FloatProperty maxAngleDiff = new FloatProperty("MaxAngle", 45.0F, 5.0F, 90.0F,
            () -> mode.getValue() == 2 && onLook.getValue());
    public final IntProperty chance = new IntProperty("Chance", 100, 0, 100,
            () -> mode.getValue() == 3 || mode.getValue() == 5);
    public final IntProperty ticksUntilJump = new IntProperty("JumpTicks", 4, 0, 20,
            () -> mode.getValue() == 3);
    public final FloatProperty intaveReduceFactor = new FloatProperty("ReduceFactor", 0.6F, 0.0F, 1.0F,
            () -> mode.getValue() == 8);
    public final BooleanProperty smartJumpSneak = new BooleanProperty("SneakReduce", false,
            () -> mode.getValue() == 12);
    public final BooleanProperty smartJumpBackward = new BooleanProperty("Backward", false,
            () -> mode.getValue() == 12);

    // ===== Unfair Knockback 模式属性 =====
    // Vanilla (14)
    public final IntProperty unfairChance = new IntProperty("U-Vanilla-Chance", 100, 0, 100,
            () -> mode.getValue() == 14);
    public final FloatProperty unfairHorizontal = new FloatProperty("U-Vanilla-Horizontal", 100.0F, 0.0F, 100.0F,
            () -> mode.getValue() == 14);
    public final FloatProperty unfairVertical = new FloatProperty("U-Vanilla-Vertical", 100.0F, 0.0F, 100.0F,
            () -> mode.getValue() == 14);
    public final FloatProperty unfairExplosionH = new FloatProperty("U-Vanilla-ExplosionH", 100.0F, 0.0F, 100.0F,
            () -> mode.getValue() == 14);
    public final FloatProperty unfairExplosionV = new FloatProperty("U-Vanilla-ExplosionV", 100.0F, 0.0F, 100.0F,
            () -> mode.getValue() == 14);
    public final BooleanProperty unfairFakeCheck = new BooleanProperty("U-Vanilla-FakeCheck", true,
            () -> mode.getValue() == 14);

    // Predict (15)
    public final BooleanProperty unfairPredInvCheck = new BooleanProperty("U-Pred-InvCheck", true,
            () -> mode.getValue() == 15);

    // Delay (17)
    public final IntProperty unfairDelayTicks = new IntProperty("U-Delay-Ticks", 2, 1, 5,
            () -> mode.getValue() == 17);

    // Polar (18)
    public final ModeProperty unfairPolarMode = new ModeProperty("U-Polar-Mode", 0, new String[]{"Reduce", "Cancel10%"},
            () -> mode.getValue() == 18);

    // GrimReduce (19)
    public final IntProperty unfairGrimMaxAir = new IntProperty("U-Grim-MaxAir", 12, 4, 20,
            () -> mode.getValue() == 19);
    public final IntProperty unfairGrimReach = new IntProperty("U-Grim-Reach", 3, 2, 4,
            () -> mode.getValue() == 19);

    private final TimerUtil knockbackTimer = new TimerUtil();
    private boolean hasReceivedKnockback = false;
    private int limitUntilJump = 0;
    private int intaveTick = 0;
    private int intaveDamageTick = 0;
    private long lastAttackTime = 0L;
    private boolean vulcanTrans = false;
    private boolean hypixelAbsorbed = false;
    private int timerTicks = 0;
    private int chanceCounter = 0;
    private boolean allowNext = true;
    private float reduceYaw = 0.0F;
    private boolean shouldRotate = false;
    private int attackTimer = -1;
    private int lastHurtTime = 0;
    private boolean jumpFlag = false;

    // ===== Unfair 模式状态 =====
    // Vanilla
    private int unfairVanillaChanceCounter = 0;
    private boolean unfairVanillaPendingExplosion = false;
    private boolean unfairVanillaAllowNext = true;
    // Reduce / Polar / GrimReduce
    private boolean unfairKb = false;
    private double unfairPolarSb = 0.0;
    // Predict
    private int unfairPredictTick = -1;
    private boolean unfairPredictSprinting = false;
    private int unfairPredictJumpResetTicks = 0;
    private Entity unfairPredictTarget = null;
    // Delay
    private boolean unfairDelayFlag = false;
    private int unfairDelayCounter = 0;
    private double unfairDelayMotionX = 0.0;
    private double unfairDelayMotionY = 0.0;
    private double unfairDelayMotionZ = 0.0;
    // GrimReduce
    private boolean unfairGrimSuspending = false;
    private int unfairGrimSuspendTicks = 0;
    private boolean unfairGrimKnockback = false;

    public KnockbackLegacy() {
        super("Knockback Legacy", false, false);
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null) {
            ((IAccessorEntityPlayer) mc.thePlayer).setSpeedInAir(0.02F);
        }
        ((IAccessorTimer) ((IAccessorMinecraft) mc).getTimer()).setTimerSpeed(1.0F);
        this.timerTicks = 0;
        this.limitUntilJump = 0;
        this.reset();
        this.chanceCounter = 0;
        this.allowNext = true;
        this.shouldRotate = false;
        this.attackTimer = -1;
        this.lastHurtTime = 0;
        this.jumpFlag = false;
        this.resetUnfairState();
    }

    private void reset() {
        this.hasReceivedKnockback = false;
        this.hypixelAbsorbed = false;
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    // ===== Unfair Knockback 辅助方法 =====

    private Entity findUnfairTarget() {
        KillAura killAura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.target != null) {
            return killAura.target.getEntity();
        }
        EntityPlayer nearest = null;
        double nearestDist = 3.0;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.deathTime > 0) continue;
            double dist = p.getDistanceToEntity(mc.thePlayer);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private boolean canUnfairDelay() {
        if (mc.theWorld == null || mc.thePlayer == null) return false;
        KillAura killAura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
        return mc.thePlayer.onGround && (killAura == null || !killAura.isEnabled() || killAura.autoBlock.getValue() == 0);
    }

    private void doUnfairReduce(Entity target) {
        if (target == null) return;
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, target);
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        mc.thePlayer.setSprinting(false);
    }

    private void releaseUnfairDelay() {
        if (unfairDelayFlag) {
            mc.thePlayer.motionX = unfairDelayMotionX;
            mc.thePlayer.motionY = unfairDelayMotionY;
            mc.thePlayer.motionZ = unfairDelayMotionZ;
            unfairDelayFlag = false;
            unfairDelayCounter = 0;
        }
    }

    private void resetUnfairState() {
        unfairVanillaChanceCounter = 0;
        unfairVanillaPendingExplosion = false;
        unfairVanillaAllowNext = true;
        unfairKb = false;
        unfairPolarSb = 0.0;
        unfairPredictTick = -1;
        unfairPredictSprinting = false;
        unfairPredictJumpResetTicks = 0;
        unfairPredictTarget = null;
        unfairDelayFlag = false;
        unfairDelayCounter = 0;
        unfairGrimSuspending = false;
        unfairGrimSuspendTicks = 0;
        unfairGrimKnockback = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.isInWater() || player.isInLava() || ((IAccessorEntity) player).getIsInWeb()) {
            return;
        }

        switch (mode.getValue()) {
            case 1:
                if (this.hasReceivedKnockback && this.knockbackTimer.hasTimeElapsed(80L)) {
                    player.motionX *= horizontal.getValue();
                    player.motionZ *= horizontal.getValue();
                    this.hasReceivedKnockback = false;
                }
                break;

            case 2:
                if (this.hasReceivedKnockback) {
                    if (!player.onGround) {
                        if (onLook.getValue()) {
                            KillAura aura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
                            Entity target = aura.target != null ? aura.target.getEntity() : null;
                            if (target != null) {
                                Rotation playerRot = new Rotation(player.rotationYaw, player.rotationPitch);
                                Rotation targetRot = this.getRotations(target);
                                if (this.getRotationDifference(playerRot, targetRot) > maxAngleDiff.getValue()) {
                                    return;
                                }
                            }
                        }
                        MoveUtil.setSpeed(MoveUtil.getSpeed() * reverseStrength.getValue());
                    } else if (this.knockbackTimer.hasTimeElapsed(80L)) {
                        this.hasReceivedKnockback = false;
                    }
                }
                break;

            case 3:
                break;

            case 4:
                if (this.hasReceivedKnockback) {
                    player.noClip = true;
                    if (player.hurtTime == 7) {
                        player.motionY = 0.4;
                    }
                    this.hasReceivedKnockback = false;
                }
                break;

            case 5:
                break;

            case 6:
                break;

            case 7:
                break;

            case 8:
                if (!this.hasReceivedKnockback) return;
                ++this.intaveTick;
                if (player.hurtTime == 2) {
                    ++this.intaveDamageTick;
                    if (player.onGround && this.intaveTick % 2 == 0 && this.intaveDamageTick <= 10) {
                        if (!((IAccessorEntityLivingBase) player).isJumping()) {
                            player.jump();
                        }
                        this.intaveTick = 0;
                    }
                    this.hasReceivedKnockback = false;
                }
                break;

            case 9:
                break;

            case 10:
                if (this.hasReceivedKnockback && player.onGround) {
                    this.hypixelAbsorbed = false;
                }
                break;

            case 11:
                break;

            case 12:
                if (player.hurtTime > 0) {
                    boolean forwardPressed = ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).getPressed();
                    if (smartJumpBackward.getValue()) {
                        if (player.hurtTime > 1) {
                            ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).setPressed(false);
                            ((IAccessorKeyBinding) mc.gameSettings.keyBindBack).setPressed(true);
                            ((IAccessorKeyBinding) mc.gameSettings.keyBindJump).setPressed(true);
                        } else if (mc.currentScreen == null) {
                            ((IAccessorKeyBinding) mc.gameSettings.keyBindForward).setPressed(
                                    GameSettings.isKeyDown(mc.gameSettings.keyBindForward));
                            ((IAccessorKeyBinding) mc.gameSettings.keyBindBack).setPressed(
                                    GameSettings.isKeyDown(mc.gameSettings.keyBindBack));
                            ((IAccessorKeyBinding) mc.gameSettings.keyBindJump).setPressed(
                                    GameSettings.isKeyDown(mc.gameSettings.keyBindJump));
                        }
                    }
                    if (player.onGround && player.hurtTime >= 8 && forwardPressed) {
                        player.jump();
                        player.motionX *= 0.9999999;
                        player.motionZ *= 0.9999999;
                    }
                    if (smartJumpSneak.getValue()) {
                        if (player.hurtTime == 9) {
                            PacketUtil.sendPacket(new C0BPacketEntityAction(player, Action.START_SNEAKING));
                            PacketUtil.sendPacket(new C0BPacketEntityAction(player, Action.STOP_SNEAKING));
                        } else if (player.hurtTime == 8) {
                            player.motionX *= 0.9999999;
                            player.motionZ *= 0.9999999;
                        }
                    }
                }
                break;

            case 13:
                int hurtTime = player.hurtTime;
                if (hurtTime > this.lastHurtTime) {
                    KillAura aura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
                    EntityLivingBase target = null;
                    if (aura != null && aura.isEnabled() && aura.target != null) {
                        target = aura.target.getEntity();
                    }
                    if (target == null) {
                        if (this.shouldRotate) {
                            Rotation currentRot = new Rotation(player.rotationYaw, player.rotationPitch);
                            float targetYaw = this.reduceYaw;
                            float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - currentRot.yaw);
                            float newYaw = currentRot.yaw + yawDiff * 0.5F;
                            player.rotationYaw = newYaw;
                            player.prevRotationYaw = newYaw;
                            if (player.onGround) {
                                player.jump();
                            }
                            this.shouldRotate = false;
                        }
                    } else {
                        double distance = player.getDistanceToEntity(target);
                        if (distance > 3.0) {
                            if (player.onGround) {
                                player.jump();
                            }
                        } else {
                            if (player.onGround) {
                                player.jump();
                            }
                            this.attackTimer = 1;
                        }
                    }
                }
                if (this.attackTimer == 0) {
                    KillAura aura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
                    EntityLivingBase target = null;
                    if (aura != null && aura.isEnabled() && aura.target != null) {
                        target = aura.target.getEntity();
                    }
                    if (target != null && player.getDistanceToEntity(target) <= 3.0) {
                        player.swingItem();
                        mc.playerController.attackEntity(player, target);
                    }
                    this.attackTimer = -1;
                }
                if (this.attackTimer > 0) {
                    --this.attackTimer;
                }
                this.lastHurtTime = hurtTime;
                break;

            // ===== Unfair Knockback 更新逻辑 =====
            case 14: // Vanilla — 纯包处理，无需 tick 更新
                break;

            case 15: // Predict
                if (unfairPredictTick < 0) break;
                {
                    int pTick = unfairPredictTick;
                    unfairPredictTick++;
                    if (pTick < 3) { // PREDICT_TICKS = 3
                        if (unfairDelayFlag) { // 非冲刺时被击退 → 延迟模式
                            if (pTick == 0) {
                                player.setSprinting(true);
                            } else if (pTick == 1) {
                                if (!BadPacketUtil.bad() && unfairPredictTarget != null) {
                                    doUnfairReduce(unfairPredictTarget);
                                }
                            } else if (pTick == 2) { // PREDICT_TICKS - 1
                                releaseUnfairDelay();
                            }
                        } else if (unfairPredictSprinting && pTick == 0) {
                            if (!BadPacketUtil.bad() && unfairPredictTarget != null) {
                                doUnfairReduce(unfairPredictTarget);
                            }
                        }
                        if (unfairPredictJumpResetTicks > 0) {
                            if (player.onGround && !isInLiquidOrWeb() && !player.isPotionActive(Potion.jump)) {
                                player.movementInput.jump = true;
                            }
                            unfairPredictJumpResetTicks--;
                        }
                    }
                    if (pTick >= 4) { // PREDICT_TICKS + POST_TICKS - 1
                        if (unfairDelayFlag) releaseUnfairDelay();
                        unfairPredictTick = -1;
                        unfairPredictTarget = null;
                        unfairPredictJumpResetTicks = 0;
                    }
                }
                break;

            case 16: // Reduce
                if (!unfairKb) break;
                if (BadPacketUtil.bad()) { unfairKb = false; break; }
                if (isInLiquidOrWeb()) { unfairKb = false; break; }
                if (!MoveUtil.isForwardPressed() || !player.isSprinting()) { unfairKb = false; break; }
                {
                    Entity reduceTarget = null;
                    KillAura aura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
                    if (aura != null && aura.isEnabled() && aura.target != null) {
                        reduceTarget = aura.target.getEntity();
                    } else {
                        RayCastUtil.RayCastResult result = RayCastUtil.rayCast(
                                new RotationUtil.RotationVec(player.rotationYaw, player.rotationPitch), 3.0F);
                        if (result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY
                                && result.entityHit instanceof EntityPlayer) {
                            reduceTarget = result.entityHit;
                        }
                    }
                    if (reduceTarget != null) {
                        doUnfairReduce(reduceTarget);
                    }
                }
                unfairKb = false;
                break;

            case 17: // Delay
                if (unfairDelayFlag) {
                    unfairDelayCounter++;
                    if (canUnfairDelay() || isInLiquidOrWeb() || unfairDelayCounter >= unfairDelayTicks.getValue()) {
                        releaseUnfairDelay();
                    }
                }
                break;

            case 18: // Polar — Reduce 模式依赖 EntityPlayer 钩子；Cancel 10% 在 onPacket 处理
                break;

            case 19: // GrimReduce
                if (unfairGrimSuspending) {
                    unfairGrimSuspendTicks++;
                    boolean timeout = unfairGrimSuspendTicks >= unfairGrimMaxAir.getValue();
                    if (player.onGround || timeout) {
                        boolean grounded = player.onGround;
                        Entity grimTarget = findUnfairTarget();
                        boolean canReduce = grounded && player.isSprinting()
                                && grimTarget != null && grimTarget.isEntityAlive() && grimTarget != mc.thePlayer
                                && !BadPacketUtil.bad();
                        releaseUnfairDelay();
                        unfairGrimSuspending = false;
                        unfairGrimSuspendTicks = 0;
                        if (canReduce) {
                            doUnfairReduce(grimTarget);
                        } else if (grounded && player.isSprinting()) {
                            player.setSprinting(false);
                        }
                    }
                    break;
                }
                if (unfairGrimKnockback) {
                    unfairGrimKnockback = false;
                    if (BadPacketUtil.bad() || isInLiquidOrWeb()) break;
                    if (!player.isSprinting()) break;
                    Entity grimTarget = findUnfairTarget();
                    if (grimTarget != null && grimTarget.isEntityAlive() && grimTarget != mc.thePlayer) {
                        doUnfairReduce(grimTarget);
                    }
                }
                break;
        }

        if (event.getType() == EventType.POST && mode.getValue() == 13 && this.jumpFlag) {
            this.jumpFlag = false;
            EntityPlayerSP playerPost = mc.thePlayer;
            if (playerPost.onGround && playerPost.isSprinting() && !playerPost.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb()) {
                playerPost.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mode.getValue() == 9) {
            IAccessorTimer timer = (IAccessorTimer) ((IAccessorMinecraft) mc).getTimer();
            if (this.timerTicks > 0 && timer.getTimerSpeed() <= 1.0F) {
                float speed = 0.8F + 0.2F * (20 - this.timerTicks) / 20.0F;
                timer.setTimerSpeed(Math.min(speed, 1.0F));
                --this.timerTicks;
            } else if (timer.getTimerSpeed() <= 1.0F) {
                timer.setTimerSpeed(1.0F);
            }
        }
    }

    @EventTarget(0)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() != player.getEntityId()) return;

            this.knockbackTimer.reset();
            IAccessorS12PacketEntityVelocity accessor = (IAccessorS12PacketEntityVelocity) packet;

            switch (mode.getValue()) {
                case 0:
                    event.setCancelled(true);
                    if (horizontal.getValue() == 0.0F && vertical.getValue() == 0.0F) return;
                    if (horizontal.getValue() != 0.0F) {
                        player.motionX = packet.getMotionX() * horizontal.getValue();
                        player.motionZ = packet.getMotionZ() * horizontal.getValue();
                    }
                    if (vertical.getValue() != 0.0F) {
                        player.motionY = packet.getMotionY() * vertical.getValue();
                    }
                    break;

                case 1:
                case 2:
                case 5:
                case 8:
                case 11:
                case 12:
                    this.hasReceivedKnockback = true;
                    break;

                case 3:
                    double motionX = packet.getMotionX();
                    double motionZ = packet.getMotionZ();
                    if (Math.abs(Math.atan2(motionX, motionZ) - Math.toRadians(player.rotationYaw)) < 2.0) {
                        this.hasReceivedKnockback = true;
                    }
                    break;

                case 4:
                    if (!player.onGround) return;
                    this.hasReceivedKnockback = true;
                    event.setCancelled(true);
                    break;

                case 6:
                    event.setCancelled(true);
                    break;

                case 7:
                    accessor.setMotionX((int) (packet.getMotionX() * 0.33));
                    accessor.setMotionZ((int) (packet.getMotionZ() * 0.33));
                    if (player.onGround) {
                        accessor.setMotionX((int) (packet.getMotionX() * 0.86));
                        accessor.setMotionZ((int) (packet.getMotionZ() * 0.86));
                    }
                    break;

                case 9:
                    if (player.onGround || player.fallDistance < 0.5F) {
                        this.hasReceivedKnockback = true;
                        event.setCancelled(true);
                    }
                    break;

                case 10:
                    this.hasReceivedKnockback = true;
                    if (!player.onGround && !this.hypixelAbsorbed) {
                        event.setCancelled(true);
                        this.hypixelAbsorbed = true;
                        return;
                    }
                    accessor.setMotionX((int) (player.motionX * 8000.0));
                    accessor.setMotionZ((int) (player.motionZ * 8000.0));
                    break;

                case 13:
                    double x = packet.getMotionX();
                    double z = packet.getMotionZ();
                    if (x != 0.0 || z != 0.0) {
                        this.reduceYaw = (float) (Math.toDegrees(Math.atan2(-z, -x)) - 90.0);
                        this.shouldRotate = true;
                    }
                    if (predictionFakeCheck.getValue() && !this.allowNext) {
                        this.allowNext = true;
                        return;
                    }
                    this.allowNext = true;
                    this.chanceCounter = (this.chanceCounter % 100) + predictionChance.getValue();
                    if (this.chanceCounter >= 100) {
                        this.jumpFlag = true;
                        if (predictionHorizontal.getValue() > 0.0F) {
                            player.motionX = x * predictionHorizontal.getValue();
                            player.motionZ = z * predictionHorizontal.getValue();
                        } else {
                            player.motionX = 0.0;
                            player.motionZ = 0.0;
                        }
                        if (predictionVertical.getValue() > 0.0F) {
                            player.motionY = packet.getMotionY() * predictionVertical.getValue();
                        } else {
                            player.motionY = 0.0;
                        }
                        if (predictionDebug.getValue()) {
                            player.addChatMessage(new ChatComponentText(
                                    String.format("Knockback (tick: %d, x: %.2f, y: %.2f, z: %.2f)",
                                            player.ticksExisted, x, packet.getMotionY(), z)));
                        }
                    } else {
                        event.setCancelled(true);
                    }
                    break;

                // ===== Unfair Knockback 模式 =====
                case 14: // Vanilla
                    if (!unfairVanillaAllowNext || !unfairFakeCheck.getValue()) {
                        unfairVanillaAllowNext = true;
                        unfairVanillaChanceCounter = unfairVanillaChanceCounter % 100 + unfairChance.getValue();
                        if (unfairVanillaChanceCounter >= 100) {
                            double hPct = unfairHorizontal.getValue() / 100.0;
                            double vPct = unfairVertical.getValue() / 100.0;
                            if (hPct > 0) {
                                accessor.setMotionX((int)(packet.getMotionX() * hPct));
                                accessor.setMotionZ((int)(packet.getMotionZ() * hPct));
                            } else {
                                accessor.setMotionX((int)(player.motionX * 8000.0));
                                accessor.setMotionZ((int)(player.motionZ * 8000.0));
                            }
                            if (vPct > 0) {
                                accessor.setMotionY((int)(packet.getMotionY() * vPct));
                            } else {
                                accessor.setMotionY((int)(player.motionY * 8000.0));
                            }
                        } else {
                            event.setCancelled(true);
                        }
                    }
                    // allowNext=true 且 fakeCheck=true → packet 正常通过（fake knockback）
                    break;

                case 15: // Predict
                    if (unfairPredictTick < 0 && !isInLiquidOrWeb()) {
                        if (unfairPredInvCheck.getValue() && mc.currentScreen instanceof GuiContainer) break;
                        Entity found = findUnfairTarget();
                        if (found != null) {
                            unfairPredictTarget = found;
                            unfairPredictSprinting = player.isSprinting();
                            unfairPredictJumpResetTicks = player.onGround ? 3 : 0;
                            if (!unfairPredictSprinting) {
                                unfairDelayMotionX = packet.getMotionX() / 8000.0;
                                unfairDelayMotionY = packet.getMotionY() / 8000.0;
                                unfairDelayMotionZ = packet.getMotionZ() / 8000.0;
                                event.setCancelled(true);
                                unfairDelayFlag = true;
                            }
                            unfairPredictTick = 0;
                        }
                    }
                    break;

                case 16: // Reduce
                    unfairKb = true;
                    break;

                case 17: // Delay
                    if (!unfairDelayFlag && !isInLiquidOrWeb()) {
                        if (!canUnfairDelay()) {
                            unfairDelayMotionX = packet.getMotionX() / 8000.0;
                            unfairDelayMotionY = packet.getMotionY() / 8000.0;
                            unfairDelayMotionZ = packet.getMotionZ() / 8000.0;
                            event.setCancelled(true);
                            unfairDelayFlag = true;
                            unfairDelayCounter = 0;
                        }
                    }
                    break;

                case 18: // Polar
                    unfairKb = true;
                    if (unfairPolarMode.getValue() == 1) {
                        RayCastUtil.RayCastResult result = RayCastUtil.rayCast(
                                new RotationUtil.RotationVec(player.rotationYaw, player.rotationPitch), 2.9F);
                        EntityLivingBase kaTarget = null;
                        KillAura aura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
                        if (aura != null && aura.target != null) {
                            kaTarget = aura.target.getEntity();
                        }
                        if (kaTarget != null && result != null
                                && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY
                                && result.entityHit instanceof EntityPlayer
                                && RotationUtil.distanceToEntity(kaTarget) > 1
                                && unfairPolarSb < 1) {
                            event.setCancelled(true);
                            unfairPolarSb++;
                        } else {
                            unfairPolarSb = Math.max(0, unfairPolarSb - 0.1);
                        }
                    }
                    break;

                case 19: // GrimReduce
                    if (unfairGrimSuspending) break;
                    if (isInLiquidOrWeb()) break;
                    if (!player.onGround) {
                        unfairDelayMotionX = packet.getMotionX() / 8000.0;
                        unfairDelayMotionY = packet.getMotionY() / 8000.0;
                        unfairDelayMotionZ = packet.getMotionZ() / 8000.0;
                        event.setCancelled(true);
                        unfairGrimSuspending = true;
                        unfairGrimSuspendTicks = 0;
                    } else {
                        unfairGrimKnockback = true;
                    }
                    break;
            }
        }

        if (event.getPacket() instanceof S27PacketExplosion) {
            S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
            IAccessorS27PacketExplosion accessor = (IAccessorS27PacketExplosion) packet;

            if (mode.getValue() == 0) {
                if (horizontal.getValue() == 0.0F && vertical.getValue() == 0.0F) {
                    event.setCancelled(true);
                } else {
                    accessor.setMotionX(accessor.getMotionX() * horizontal.getValue());
                    accessor.setMotionY(accessor.getMotionY() * vertical.getValue());
                    accessor.setMotionZ(accessor.getMotionZ() * horizontal.getValue());
                }
            } else if (mode.getValue() == 3) {
                this.hasReceivedKnockback = true;
            } else if (mode.getValue() == 14) { // Vanilla 爆炸
                if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                    unfairVanillaPendingExplosion = true;
                    if (unfairExplosionH.getValue() == 0.0F || unfairExplosionV.getValue() == 0.0F) {
                        event.setCancelled(true);
                    } else {
                        accessor.setMotionX(accessor.getMotionX() * unfairExplosionH.getValue() / 100.0F);
                        accessor.setMotionY(accessor.getMotionY() * unfairExplosionV.getValue() / 100.0F);
                        accessor.setMotionZ(accessor.getMotionZ() * unfairExplosionH.getValue() / 100.0F);
                    }
                }
            } else if (mode.getValue() == 13) {
                if (predictionDebug.getValue()) {
                    player.addChatMessage(new ChatComponentText(
                            String.format("Explosion (tick: %d, x: %.2f, y: %.2f, z: %.2f)",
                                    player.ticksExisted,
                                    player.motionX + packet.func_149149_c(),
                                    player.motionY + packet.func_149144_d(),
                                    player.motionZ + packet.func_149147_e())));
                }
                if (predictionHorizontal.getValue() == 0.0F || predictionVertical.getValue() == 0.0F) {
                    event.setCancelled(true);
                }
            }
        }

        if (mode.getValue() == 6 && event.getPacket() instanceof S32PacketConfirmTransaction) {
            event.setCancelled(true);
            S32PacketConfirmTransaction p = (S32PacketConfirmTransaction) event.getPacket();
            PacketUtil.sendPacket(new C0FPacketConfirmTransaction(p.getWindowId(), p.getActionNumber(), this.vulcanTrans));
            this.vulcanTrans = !this.vulcanTrans;
        }

        // Vanilla: S19 受击动画 → 标记下一次速度包为真实击退
        if (mode.getValue() == 14 && event.getPacket() instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus s19 = (S19PacketEntityStatus) event.getPacket();
            if (s19.getEntity(mc.theWorld) == mc.thePlayer && s19.getOpCode() == 2) {
                unfairVanillaAllowNext = false;
            }
        }
    }

    @EventTarget
    public void onJump(JumpEvent event) {
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mode.getValue() == 8) {
            if (mc.thePlayer.hurtTime == 9 && System.currentTimeMillis() - this.lastAttackTime <= 8000L) {
                mc.thePlayer.motionX *= intaveReduceFactor.getValue();
                mc.thePlayer.motionZ *= intaveReduceFactor.getValue();
            }
            this.lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (mode.getValue() == 3 && this.hasReceivedKnockback) {
            if (!((IAccessorEntityLivingBase) mc.thePlayer).isJumping() &&
                    RandomUtil.nextInt(0, 100) < chance.getValue() &&
                    this.limitUntilJump >= ticksUntilJump.getValue() &&
                    mc.thePlayer.isSprinting() &&
                    mc.thePlayer.onGround &&
                    mc.thePlayer.hurtTime == 9) {
                mc.thePlayer.jump();
                this.limitUntilJump = 0;
            }
            this.hasReceivedKnockback = false;
        }
        if (mc.thePlayer.hurtTime == 9) {
            ++this.limitUntilJump;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    private Rotation getRotations(Entity entity) {
        double x = entity.posX - mc.thePlayer.posX;
        double z = entity.posZ - mc.thePlayer.posZ;
        double y = entity.posY + entity.getEyeHeight() - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new Rotation(yaw, pitch);
    }

    private float getRotationDifference(Rotation a, Rotation b) {
        return Math.abs(MathHelper.wrapAngleTo180_float(a.yaw - b.yaw));
    }
}
