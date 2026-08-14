package elara.module.combat;

import com.google.common.base.CaseFormat;
import elara.event.EventManager;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.*;
import elara.mixin.IAccessorEntity;
import elara.module.Module;
import elara.module.movement.LongJump;
import elara.module.movement.Stasis;
import elara.module.world.Scaffold;
import elara.property.properties.BooleanProperty;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import elara.property.properties.PercentProperty;
import elara.util.ChatUtil;
import elara.util.MoveUtil;
import elara.util.RayCastUtil;
import elara.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import elara.Elara;
import elara.enums.BlinkModules;
import elara.enums.DelayModules;
import elara.module.movement.KeepSprint;
import elara.util.PacketUtil;
import net.minecraftforge.fml.common.gameevent.TickEvent;


import java.util.Objects;

public class Knockback extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Vanilla","Prediction"});
    public final BooleanProperty reduce = new BooleanProperty("Reduce", true, () -> mode.getValue() == 1);
    public final ModeProperty reduceMode = new ModeProperty("ReduceMode", 0, new String[]{"Attack", "ReleaseWhenCanAttack", "ReleaseBeforeCanAttack", "Blink"}, () -> mode.getValue() == 1 && reduce.getValue());
    public final IntProperty startBlinkHurtTime = new IntProperty("StartBlinkHurtTime", 1, 0, 10, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() == 3);
    public final IntProperty startReleaseTicks = new IntProperty("StartReleaseTicks", 1, 0, 5, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() == 3);
    public final BooleanProperty forceBlocking = new BooleanProperty("ForceBlocking", true, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() == 3);
    private final BooleanProperty extraAttack = new BooleanProperty("ExtraAttack", false, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() != 0);
    private final BooleanProperty reduceWhenCanAttack = new BooleanProperty("Reduce When Can Attack", true, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() == 0);
    public final BooleanProperty cancelKillAuraAttack = new BooleanProperty("CancelKillAuraAttack", false, () -> mode.getValue() == 1 && reduce.getValue() && reduceMode.getValue() == 0);
    private final BooleanProperty onlySprinting = new BooleanProperty("Only Sprinting", true, () -> mode.getValue() == 1 && reduceMode.getValue() == 0 && reduce.getValue());
    public final BooleanProperty smartTimes = new BooleanProperty("SmartTimes",true, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0);
    public final IntProperty attackTimes = new IntProperty("Attack Times", 1, 1, 5, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0 && !smartTimes.getValue());
    public final BooleanProperty keepSprint = new BooleanProperty("KeepSprint",false, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0);
    public final BooleanProperty testMode = new BooleanProperty("TestMode",false, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0);
    private final IntProperty stopBlockHurtTime = new IntProperty("StopBlockHurtTime",2,0,10, () -> this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0 && testMode.getValue());

    public final BooleanProperty jump = new BooleanProperty("Jump", true, () -> mode.getValue() == 1);
    public final BooleanProperty delay = new BooleanProperty("Delay", false, () -> mode.getValue() == 1);
    public final IntProperty delayTicks = new IntProperty("Delay Ticks", 1, 1, 5, () -> mode.getValue() == 1 && delay.getValue() && !this.airBuffer.getValue());
    public final BooleanProperty forceDelayRisingToFalling = new BooleanProperty("Force Delay Rising To Falling",false,() -> mode.getValue() == 1 && delay.getValue() && !this.airBuffer.getValue());
    public final BooleanProperty airBuffer = new BooleanProperty("Delay Till On Ground", true, () -> mode.getValue() == 1 && delay.getValue());
    public final BooleanProperty groundDelay = new BooleanProperty("Ground Delay", false, () -> mode.getValue() == 1 && delay.getValue() && !airBuffer.getValue());
    public final BooleanProperty rotate = new BooleanProperty("Rotate", false, () -> this.mode.getValue() == 1);
    public final IntProperty rotateTick = new IntProperty("Rotate Ticks", 3, 1, 12, () -> this.mode.getValue() == 1 && this.rotate.getValue());
    public final BooleanProperty autoMove = new BooleanProperty("Auto Move", false, () -> this.mode.getValue() == 1 && this.rotate.getValue());
    public final PercentProperty chance = new PercentProperty("Chance", 100, () -> mode.getValue() == 0);
    public final PercentProperty horizontal = new PercentProperty("Horizontal", 100, () -> mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("Vertical", 100, () -> mode.getValue() == 0);
    public final PercentProperty explosionHorizontal = new PercentProperty("Explosions Horizontal", 100, () -> mode.getValue() == 0);
    public final PercentProperty explosionVertical = new PercentProperty("Explosions Vertical", 100, () -> mode.getValue() == 0);
    public final BooleanProperty fakeCheck = new BooleanProperty("Fake Check", true);
    public final BooleanProperty debug = new BooleanProperty("Debug", false);
    public boolean knockback = false;
    private int chanceCounter = 0;
    private int rotateTickCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean delayFlag = false;
    private boolean jumpFlag = false;
    public static boolean hasReceivedKnockback;
    private int ticksSinceKnockback = -1;

    private double knockbackX = 0;
    private float[] targetRotation = null;
    private double knockbackZ = 0;
    public int reduceTick = -1;
    public int hitCount;
    public static boolean extraAttacked,knockbackAttacked = false;
    public static boolean stoppedBlock = false;
    public static boolean cancellingKillAuraAttack = false;
    public static boolean blinkActive = false;
    private boolean blinkingKnockback = false;
    private boolean blinkScheduled = false;
    private int knockbackTimer = -1;
    public Knockback() {
        super("Knockback", false, false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    private int computeReduceTicks(int motionX, int motionZ) {
        double kb = Math.hypot(motionX, motionZ);
        double ticksExact = 0.000643153527 * kb + 2.9419087136;
        int ticks = (int) Math.round(ticksExact);

        if (ticks < 1) ticks = 1;
        if (ticks > 10) ticks = 10;

        return ticks;
    }
    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!allowNext || !(Boolean) fakeCheck.getValue()) {
            allowNext = true;
            if (pendingExplosion) {
                if (mode.getValue() == 0) {
                    pendingExplosion = false;
                    if (explosionHorizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) explosionHorizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) explosionHorizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (explosionVertical.getValue() > 0) {
                        event.setY(event.getY() * (double) explosionVertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            } else {
                if (!isEnabled() || event.isCancelled()) {
                    pendingExplosion = false;
                    allowNext = true;
                    return;
                }
                if (this.mode.getValue() == 1 && this.rotate.getValue() && event.getY() > 0.0) {
                    this.knockbackX = event.getX();
                    this.knockbackZ = event.getZ();
                    if (Math.abs(this.knockbackX) > 0.01 || Math.abs(this.knockbackZ) > 0.01) {
                        this.rotateTickCounter = 1;
                    }
                }
                if (mode.getValue() == 1 && smartTimes.getValue()){
                    hitCount = computeReduceTicks((int) event.getX(), (int) event.getZ());
                }
                if (delay.getValue() && !groundDelay.getValue() && mc.thePlayer.onGround){
                    if (jump.getValue() && this.mode.getValue() == 1){
                        jumpFlag = true;
                    }
                    ticksSinceKnockback = 0;
                }
                if (!delay.getValue())ticksSinceKnockback = 0;
                chanceCounter = chanceCounter % 100 + chance.getValue();
                if (chanceCounter >= 100) {
                    if (mode.getValue() == 0) {
                        if (horizontal.getValue() > 0) {
                            event.setX(event.getX() * (double) horizontal.getValue() / 100.0);
                            event.setZ(event.getZ() * (double) horizontal.getValue() / 100.0);
                        } else {
                            event.setX(mc.thePlayer.motionX);
                            event.setZ(mc.thePlayer.motionZ);
                        }
                        if (vertical.getValue() > 0) {
                            event.setY(event.getY() * (double) vertical.getValue() / 100.0);
                        } else {
                            event.setY(mc.thePlayer.motionY);
                        }
                    }
                }
            }
        }

    }


    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && this.jumpFlag) {
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !mc.thePlayer.isPotionActive(Potion.jump) && !this.isInLiquidOrWeb() && mc.thePlayer.isSprinting()) {
                mc.thePlayer.movementInput.jump = true;
            }
            this.jumpFlag = false;
        }
    }
    @EventTarget
    public void onTick(TickEvent event){
        if (this.isEnabled()){
            if (testMode.getValue() && this.mode.getValue() == 1 && this.reduce.getValue() && reduceMode.getValue() == 0){
                if (ticksSinceKnockback >= stopBlockHurtTime.getValue()){
                    hasReceivedKnockback = true;
                    stoppedBlock = true;
                }
            }
            if (ticksSinceKnockback >= 0) {
                ticksSinceKnockback++;
            }
            if (ticksSinceKnockback >= 10) {
                ticksSinceKnockback = -1;
            }
        }
    }
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (event.getType() == EventType.PRE) {
            cancellingKillAuraAttack = false;
            int maxTick = this.rotateTick.getValue();
            if (this.rotateTickCounter > 0 && this.rotateTickCounter <= maxTick) {
                if (this.rotateTickCounter == 1) {
                    double deltaX = -this.knockbackX;
                    double deltaZ = -this.knockbackZ;
                    this.targetRotation = RotationUtil.getRotationsTo(deltaX, 0, deltaZ, event.getYaw(), event.getPitch());
                }
                if (this.targetRotation != null && !Elara.moduleManager.getModule(Scaffold.class).isEnabled()) {
                    event.setRotation(this.targetRotation[0], this.targetRotation[1], 2);
                    event.setPervRotation(this.targetRotation[0], 2);
                }
            }
        }
        if (event.getType() == EventType.PRE){
            int maxTick = this.rotateTick.getValue();
            if (this.rotateTickCounter > 0 && this.rotateTickCounter <= maxTick) {
                this.rotateTickCounter++;
                if (this.rotateTickCounter > maxTick) {
                    this.rotateTickCounter = 0;
                    this.targetRotation = null;
                    this.knockbackX = 0;
                    this.knockbackZ = 0;
                }
            }
        }
        if (mode.getValue() == 1) {
            if (reduce.getValue() && reduceMode.getValue() == 3 && event.getType() == EventType.PRE) {
                if (knockbackTimer >= 0) {
                    knockbackTimer++;
                }
                if (blinkingKnockback) {
                    if (knockbackTimer >= startReleaseTicks.getValue()) {
                        releaseKnockbackBlink();
                    }
                } else if (knockback && mc.thePlayer.hurtTime <= startBlinkHurtTime.getValue()) {
                    if (forceBlocking.getValue()) {
                        KillAura killAura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
                        if (killAura != null && killAura.isEnabled() && killAura.isPlayerBlocking()) {
                            startKnockbackBlink();
                        } else {
                            blinkScheduled = true;
                        }
                    } else {
                        startKnockbackBlink();
                    }
                } else if (blinkScheduled) {
                    if (knockbackTimer >= startReleaseTicks.getValue()) {
                        blinkScheduled = false;
                        knockback = false;
                        knockbackTimer = -1;
                    } else {
                        KillAura killAura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
                        if (killAura != null && killAura.isEnabled() && killAura.isPlayerBlocking()) {
                            startKnockbackBlink();
                        }
                    }
                }
            }
            if (reduce.getValue() && reduceMode.getValue() == 0) {
                if (event.getType() == EventType.PRE) {
                    if (knockbackAttacked) {
                        KillAura killAura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
                        if (killAura.getTarget() != null && killAura.isEnabled() && mc.thePlayer.isSprinting()) {
                            ChatUtil.sendFormatted("Attack");
                            EventManager.call(new AttackEvent(killAura.getTarget()));
                            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                            if (killAura.getTarget() != mc.thePlayer) {
                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(killAura.getTarget(), C02PacketUseEntity.Action.ATTACK));
                            } else {
                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(Objects.requireNonNull(killAura.getTarget()), C02PacketUseEntity.Action.ATTACK));
                            }
                            mc.thePlayer.motionX *= 0.6D;
                            mc.thePlayer.motionZ *= 0.6D;
                            mc.thePlayer.setSprinting(false);
                        }
                        else {
                            extraAttacked = false;
                        }
                        knockbackAttacked = false;
                    }
                    if (hasReceivedKnockback) {
                        if (smartTimes.getValue()){
                            if (reduceTick >= hitCount) {
                                reduceTick = 0;
                                hasReceivedKnockback = false;
                                stoppedBlock = false;
                            }
                        }else {
                            if (reduceTick >= attackTimes.getValue()) {
                                reduceTick = 0;
                                hasReceivedKnockback = false;
                                stoppedBlock = false;
                            }
                        }
                        RayCastUtil.RayCastResult targetA = RayCastUtil.rayCast(new RotationUtil.RotationVec(event.getYaw(), event.getPitch()), 3);
                        if (targetA != null && reduceMode.getValue() == 0) {
                            if (targetA.entityHit instanceof EntityPlayer && targetA.entityHit != mc.thePlayer) {
                                if (mc.thePlayer.isSprinting() || !this.onlySprinting.getValue()) {
                                    KillAura killAura = (KillAura) Elara.moduleManager.getModule(KillAura.class);
                                    if (killAura.getTarget() != null) {
                                        if (!reduceWhenCanAttack.getValue()
                                                || killAura.knockbackCanReduce(0, killAura.blockTick)) {
                                            if (cancelKillAuraAttack.getValue()) cancellingKillAuraAttack = true;
                                            EventManager.call(new AttackEvent(killAura.getTarget()));
                                            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                                            if (killAura.getTarget() != mc.thePlayer) {
                                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(killAura.getTarget(), C02PacketUseEntity.Action.ATTACK));
                                            } else {
                                                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(Objects.requireNonNull(killAura.getTarget()), C02PacketUseEntity.Action.ATTACK));
                                            }
                                            mc.thePlayer.motionX *= 0.6D;
                                            mc.thePlayer.motionZ *= 0.6D;
                                            if (!keepSprint.getValue()) mc.thePlayer.setSprinting(false);
                                        }
                                    } else {
                                        if (cancelKillAuraAttack.getValue()) cancellingKillAuraAttack = true;
                                        EventManager.call(new AttackEvent(targetA.entityHit));
                                        mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                                        if (targetA.entityHit != mc.thePlayer) {
                                            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(targetA.entityHit, C02PacketUseEntity.Action.ATTACK));
                                        } else {
                                            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(Objects.requireNonNull(targetA.entityHit), C02PacketUseEntity.Action.ATTACK));
                                        }
                                        mc.thePlayer.motionX *= 0.6D;
                                        mc.thePlayer.motionZ *= 0.6D;
                                        if (!keepSprint.getValue()) mc.thePlayer.setSprinting(false);
                                    }
                                }
                            }
                        }
                        reduceTick++;
                    }
                }
            }
            if (event.getType() == EventType.POST) {
                KillAura killAura = (KillAura)Elara.moduleManager.getModule(KillAura.class);
                if (delayFlag && (!forceDelayRisingToFalling.getValue() || mc.thePlayer.motionY <= 0.0)
                        && ((delay.getValue()
                        && (isInLiquidOrWeb() || Elara.delayManager.getDelay() >= (long) delayTicks.getValue() && !airBuffer.getValue()) || (mc.thePlayer.onGround && !groundDelay.getValue() && !airBuffer.getValue()))
                        || (airBuffer.getValue() && mc.thePlayer.onGround && delayFlag)) || (reduceMode.getValue() == 1
                        && killAura.knockbackCanReduce(1, killAura.blockTick)
                        && killAura.shouldAutoBlock() && reduce.getValue()) || (reduceMode.getValue() == 2
                        && killAura.knockbackCanReduce(2, killAura.blockTick)
                        && killAura.shouldAutoBlock() && reduce.getValue())) {
                    ticksSinceKnockback = 0;
                    if (killAura.getTarget() != null) {
                        if (extraAttack.getValue() && reduce.getValue() && reduceMode.getValue() != 0) {
                            if (!extraAttacked) {
                                extraAttacked = true;
                                knockbackAttacked = true;
                            }
                        }
                    }
                    if (!testMode.getValue()) {
                        hasReceivedKnockback = true;
                    }
                    dbg(Elara.clientName + "Delay/Buffer " + Elara.delayManager.getDelay() + " Ticks");
                    Elara.delayManager.setDelayState(false, DelayModules.KNOCKBACK);
                    delayFlag = false;
                    if (jump.getValue() && this.mode.getValue() == 1){
                        jumpFlag = true;
                    }
                }
            }
        }
    }

    private void startKnockbackBlink() {
        if (Elara.blinkManager.setBlinkState(true, BlinkModules.KNOCKBACK)) {
            blinkingKnockback = true;
            blinkActive = true;
            blinkScheduled = false;
        }
    }

    private void releaseKnockbackBlink() {
        if (!blinkingKnockback) return;
        boolean wasActive = blinkActive;
        blinkActive = false;
        KeepSprint keepSprint = (KeepSprint) Elara.moduleManager.getModule(KeepSprint.class);
        double factor = keepSprint != null && keepSprint.isEnabled() ? keepSprint.getSlowFactor() : 0.6;
        blinkActive = wasActive;
        boolean wasBlinking = Elara.blinkManager.isBlinking();
        Elara.blinkManager.blinking = false;
        for (net.minecraft.network.Packet<?> p : Elara.blinkManager.blinkedPackets) {
            if (p instanceof C02PacketUseEntity) {
                mc.thePlayer.motionX *= factor;
                mc.thePlayer.motionZ *= factor;
            }
            PacketUtil.sendPacketNoEvent(p);
        }
        Elara.blinkManager.blinkedPackets.clear();
        if (!wasBlinking) {
            Elara.blinkManager.blinkModule = BlinkModules.NONE;
        }
        blinkingKnockback = false;
        blinkActive = false;
        blinkScheduled = false;
        knockback = false;
        knockbackTimer = -1;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    if (!testMode.getValue()) {
                        if (!delay.getValue()) {
                            hasReceivedKnockback = true;
                        }
                        if (delay.getValue() && !groundDelay.getValue() && mc.thePlayer.onGround) {
                            hasReceivedKnockback = true;
                        }
                    }
                    LongJump longJump = (LongJump) Elara.moduleManager.modules.get(LongJump.class);
                    if (mode.getValue() == 1
                            && !delayFlag
                            && !isInLiquidOrWeb()
                            && !pendingExplosion
                            && !Elara.moduleManager.getModule(Stasis.class).isEnabled()
                            && (!allowNext || !(Boolean) fakeCheck.getValue())
                            && (!longJump.isEnabled() || !longJump.canStartJump())) {
                        if ((airBuffer.getValue() && !mc.thePlayer.onGround) || (delay.getValue() && !mc.thePlayer.onGround) || (delay.getValue() && groundDelay.getValue() && !airBuffer.getValue())) {
                            Elara.delayManager.setDelayState(true, DelayModules.KNOCKBACK);
                            dbg(Elara.clientName + "Delay/Buffer Active");
                            Elara.delayManager.delayedPacket.offer(packet);
                            event.setCancelled(true);
                            delayFlag = true;
                        }
                    }
                }
            } else if (!(event.getPacket() instanceof S27PacketExplosion)) {
                if (event.getPacket() instanceof S19PacketEntityStatus) {
                    S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
                    Entity entity = packet.getEntity(mc.theWorld);
                    if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                        allowNext = false;
                    }
                }
            } else if (mode.getValue() == 0) {
                S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
                if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                    pendingExplosion = true;
                    if (explosionHorizontal.getValue() == 0 || explosionVertical.getValue() == 0) {
                        event.setCancelled(true);
                    }
                }
            }
        }
        if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity knockbackPacket = (S12PacketEntityVelocity) event.getPacket();
                if (knockbackPacket.getEntityID() == mc.thePlayer.getEntityId()) {
                    knockback = true;
                    knockbackTimer = 0;
                }
            }
        }
    }
    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled() && this.rotateTickCounter > 0 && this.rotateTickCounter <= this.rotateTick.getValue()) {
            if (this.autoMove.getValue()) {
                mc.thePlayer.movementInput.moveForward = 1.0F;
            }
        }
    }
    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        onDisabled();
    }

    public void dbg(String msg) {
        if (debug.getValue()) ChatUtil.sendFormatted(msg);
    }

    @Override
    public void onEnabled() {
        knockback = false;
        hasReceivedKnockback = false;
        this.rotateTickCounter = 0;
        this.targetRotation = null;
        this.knockbackX = 0;
        this.knockbackZ = 0;
    }

    @Override
    public void onDisabled() {
        pendingExplosion = false;
        stoppedBlock = false;
        allowNext = true;
        hasReceivedKnockback = false;
        knockback = false;
        cancellingKillAuraAttack = false;
        if (blinkingKnockback) {
            blinkingKnockback = false;
            blinkActive = false;
            blinkScheduled = false;
            knockbackTimer = -1;
            Elara.blinkManager.blinking = false;
            Elara.blinkManager.blinkedPackets.clear();
            Elara.blinkManager.blinkModule = BlinkModules.NONE;
        }
    }
    @Override
    public String[] getSuffix() {
        if (mode.getValue() == 0) {
            return new String[]{
                    String.format("%d%%", horizontal.getValue()),
                    String.format("%d%%", vertical.getValue())
            };
        } else {
            return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
        }
    }
}