package elara.module.combat;

import com.google.common.base.CaseFormat;
import elara.module.render.HUD;
import elara.module.world.AutoBlockIn;
import elara.module.world.BedBreaker;
import elara.module.world.Scaffold;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import elara.Elara;
import elara.enums.BlinkModules;
import elara.event.EventManager;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.event.types.Priority;
import elara.events.*;
import elara.management.RotationState;
import elara.mixin.IAccessorMinecraft;
import elara.mixin.IAccessorPlayerControllerMP;
import elara.module.Module;
import elara.property.properties.*;
import elara.util.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode;
    public final ModeProperty sort;
    public ModeProperty autoBlock;
    public ModeProperty hypixelMode;
    private final BooleanProperty noSwap = new BooleanProperty("NoSwap", true, () -> this.autoBlock.getValue() == 2);
    private final BooleanProperty test = new BooleanProperty("MoreAttack", false, () -> this.autoBlock.getValue() == 2);
    private final IntProperty moreAttackDelay = new IntProperty("MoreAttackDelay", 1, 0, 3, () -> this.autoBlock.getValue() == 2 && test.getValue());
    private final IntProperty maxTick = new IntProperty("MaxTick", 3, 1, 5, () -> this.autoBlock.getValue() == 6);
    private final IntProperty startBlinkTick = new IntProperty("StartBlinkTick", 0, 1, 5, () -> this.autoBlock.getValue() == 6);
    private final IntProperty stopBlinkTick = new IntProperty("StopBlinkTick", 2, 1, 5, () -> this.autoBlock.getValue() == 6);
    private final IntProperty swapTick = new IntProperty("SwapTick", 2, 1, 5, () -> this.autoBlock.getValue() == 6);
    private final IntProperty switchBackTick = new IntProperty("SwitchBackTick", 2, 1, 5, () -> this.autoBlock.getValue() == 6);
    private final IntProperty stopBlockTick = new IntProperty("StopBlockTick", 2, 1, 5, () -> this.autoBlock.getValue() == 6);
    public final IntProperty attackTick = new IntProperty("AttackTick", 0, 1, 5, () -> this.autoBlock.getValue() == 6);
    private final IntProperty startBlockTick = new IntProperty("StartBlockTick", 0, 1, 5, () -> this.autoBlock.getValue() == 6);
    private final BooleanProperty postStartBlock = new BooleanProperty("PostBlock", false, () -> this.autoBlock.getValue() == 6);
    private final IntProperty predictHoldTicks = new IntProperty("PredictHold", 2, 1, 5, () -> this.autoBlock.getValue() == 7);
    private final BooleanProperty forceBlockAnim = new BooleanProperty("Force Block Anim", true,
            () -> this.autoBlock.getValue() == 3 || this.autoBlock.getValue() == 7);
    public final BooleanProperty autoBlockRequirePress;
    public final IntProperty autoBlockCPS;
    public final FloatProperty autoBlockRange;

    public final FloatProperty swingRange;
    public final FloatProperty attackRange;
    public final IntProperty fov;
    public final IntProperty minCPS;
    public final IntProperty maxCPS;
    public final IntProperty switchDelay;
    public final ModeProperty rotations;
    public final ModeProperty moveFix;
    public final PercentProperty smoothing;
    public final IntProperty angleStep;
    public final BooleanProperty throughWalls;
    public final BooleanProperty requirePress;
    public final BooleanProperty allowMining;
    public final BooleanProperty weaponsOnly;
    public final BooleanProperty allowTools;
    public final BooleanProperty inventoryCheck;
    public final BooleanProperty lowTimerCheck;
    public final BooleanProperty botCheck;
    public final BooleanProperty players;
    public final BooleanProperty bosses;
    public final BooleanProperty mobs;
    public final BooleanProperty animals;
    public final BooleanProperty golems;
    public final BooleanProperty silverfish;
    public final BooleanProperty teams;
    public ModeProperty showTarget;

    private final TimerUtil timer = new TimerUtil();
    public AttackData target = null;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private boolean blockingState = false;
    private boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private long attackDelayMS = 0L;
    public int blockTick = 0;
    private boolean swapped = false;
    private boolean postBlock = false;
    private boolean postSwap = false;
    private int testAttackTick = 0;

    // PredictAB state — 预判敌方攻击并自动格挡
    // 0=IDLE  1=BLOCKING  2=RECOVER
    private int predictState = 0;
    private int predictTick = 0;
    private float predictPrevSwing = 0.0f;
    private int predictLastAttackTick = -1;
    private int predictPrevHurtTime = 0;

    public KillAura() {
        super("KillAura", false);
        this.mode = new ModeProperty("Mode", 0, new String[]{"Single", "Switch"});
        this.sort = new ModeProperty("Sort", 0, new String[]{"Distance", "Health", "Hurt Time", "FOV"});

        this.autoBlock = new ModeProperty(
                "AutoBlock", 0, new String[]{"None", "Vanilla", "Hypixel", "Legit", "Fake", "Hypixel Test", "Hypixel Custom", "Predict"}
        );
        this.hypixelMode = new ModeProperty(
                "HypixelMode", 0, new String[]{"OldHypixel", "Without NoSlow", "Custom", "Lag"}, () -> this.autoBlock.getValue() == 2
        );
        this.autoBlockRequirePress = new BooleanProperty("AutoBlock Require Press", false);
        this.autoBlockCPS = new IntProperty("AutoBlock Aps", 10, 1, 20);
        this.autoBlockRange = new FloatProperty("AutoBlock Range", 6.0F, 3.0F, 8.0F);
        this.swingRange = new FloatProperty("Swing Range", 3.5F, 3.0F, 6.0F);
        this.attackRange = new FloatProperty("Attack Range", 3.0F, 3.0F, 6.0F);
        this.fov = new IntProperty("Fov", 360, 30, 360);
        this.minCPS = new IntProperty("Min Aps", 14, 1, 20);
        this.maxCPS = new IntProperty("Max Aps", 14, 1, 20);
        this.switchDelay = new IntProperty("Switch Delay", 150, 0, 1000);
        this.rotations = new ModeProperty("Rotations", 2, new String[]{"None", "Legit", "Silent", "Lock View"});
        this.moveFix = new ModeProperty("Move Fix", 1, new String[]{"None", "Silent", "Strict"});
        this.smoothing = new PercentProperty("Smoothing", 0);
        this.angleStep = new IntProperty("Angle Step", 90, 30, 180);
        this.throughWalls = new BooleanProperty("Through Walls", true);
        this.requirePress = new BooleanProperty("Require Press", false);
        this.allowMining = new BooleanProperty("Allow Mining", false);
        this.weaponsOnly = new BooleanProperty("Weapons Only", false);
        this.allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
        this.inventoryCheck = new BooleanProperty("Inventory Check", true);
        this.lowTimerCheck = new BooleanProperty("Low Timer Check", true);
        this.botCheck = new BooleanProperty("Bot Check", true);
        this.players = new BooleanProperty("Players", true);
        this.bosses = new BooleanProperty("Bosses", false);
        this.mobs = new BooleanProperty("Mobs", false);
        this.animals = new BooleanProperty("Animals", false);
        this.golems = new BooleanProperty("Golems", false);
        this.silverfish = new BooleanProperty("Silverfish", false);
        this.teams = new BooleanProperty("Teams", true);
        this.showTarget = new ModeProperty("Show Target", 0, new String[]{"None", "Default", "Hud"});
    }

    private long getAttackDelay() {
        return this.isBlocking ? (long) (1000.0F / this.autoBlockCPS.getValue()) : 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private boolean performAttack(float yaw, float pitch) {
        if (!Elara.playerStateManager.digging && !Elara.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) {
                return false;
            } else if (this.attackDelayMS > 0L) {
                return false;
            } else if (((IAccessorMinecraft) mc).getTimer().timerSpeed < 1F && lowTimerCheck.getValue()) {
                return false;
            } else {
                this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
                mc.thePlayer.swingItem();
                if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(this.target.getBox()))
                        && RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, this.attackRange.getValue()) == null) {
                    return false;
                } else {
                    AttackEvent event = new AttackEvent(this.target.getEntity());
                    EventManager.call(event);
                    ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                    PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.ATTACK));
                    if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
                        PlayerUtil.attackEntity(this.target.getEntity());
                    }
                    this.hitRegistered = true;
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private void sendUseItem() {
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        this.startBlock(mc.thePlayer.getHeldItem());
    }

    private void startBlock(ItemStack itemStack) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private void interactAttack(float yaw, float pitch) {
        if (this.target != null) {
            MovingObjectPosition mop = RotationUtil.rayTrace(this.target.getBox(), yaw, pitch, 8.0);
            if (mop != null) {
                ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
                PacketUtil.sendPacket(
                        new C02PacketUseEntity(
                                this.target.getEntity(),
                                new Vec3(mop.hitVec.xCoord - this.target.getX(), mop.hitVec.yCoord - this.target.getY(), mop.hitVec.zCoord - this.target.getZ())
                        )
                );
                PacketUtil.sendPacket(new C02PacketUseEntity(this.target.getEntity(), Action.INTERACT));
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                this.blockingState = true;
            }
        }
    }

    private boolean canAttack() {
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
            return false;
        } else if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (((IAccessorPlayerControllerMP) mc.playerController).getIsHittingBlock()) {
                return false;
            } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
                return false;
            } else {
                AutoHeal autoHeal = (AutoHeal) Elara.moduleManager.getModule(AutoHeal.class);
                if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
                    return false;
                } else {
                    BedBreaker bedBreaker = (BedBreaker) Elara.moduleManager.getModule(BedBreaker.class);
                    AutoBlockIn autoBlockIn = (AutoBlockIn) Elara.moduleManager.getModule(AutoBlockIn.class);
                    if (bedBreaker.isEnabled() && bedBreaker.isReady()) {
                        return false;
                    } else if (Elara.moduleManager.getModule(Scaffold.class).isEnabled()) {
                        return false;
                    } else if (autoBlockIn.isEnabled()) {
                        return false;
                    } else if (this.requirePress.getValue()) {
                        return PlayerUtil.isAttacking();
                    } else {
                        return !this.allowMining.getValue() || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK) || !PlayerUtil.isAttacking();
                    }
                }
            }
        } else {
            return false;
        }
    }

    private boolean canAutoBlock() {
        if (!ItemUtil.isHoldingSword()) {
            return false;
        } else {
            return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
        }
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .anyMatch(
                        entity -> entity instanceof EntityLivingBase
                                && this.isValidTarget((EntityLivingBase) entity)
                                && this.isInBlockRange((EntityLivingBase) entity)
                );
    }

    /**
     * PredictAB — 预判敌方攻击
     *
     * 多路径检测，任一命中即触发格挡：
     * 1. Swing 上升沿：敌方挥剑动画起手的瞬间（最快视觉反应）
     * 2. HurtTime 确认：已被命中（确认型，用于校准预判周期）
     * 3. 冷却预判：基于上次攻击间隔，预测下一次攻击窗口
     * 4. 近距离威胁：敌方面对自己且在极近距离、刚结束无敌帧
     */
    private boolean predictEnemyAttack(EntityLivingBase target) {
        if (target == null) {
            predictPrevHurtTime = mc.thePlayer.hurtTime;
            return false;
        }

        double dist = RotationUtil.distanceToEntity(target);
        boolean result = false;

        if (dist <= (double) this.autoBlockRange.getValue()) {
            float sp = target.swingProgress;
            float spPrev = this.predictPrevSwing;
            float angleBetween = RotationUtil.angleToEntity(target);
            boolean facingUs = angleBetween <= 90.0f;

            // PATH 1: Swing 上升沿 — 敌方刚起手挥剑
            boolean swingRising = spPrev <= 0.05f && sp > 0.05f;
            if (swingRising && facingUs && dist <= 4.5) {
                predictLastAttackTick = mc.thePlayer.ticksExisted;
                result = true;
            }

            // PATH 2: HurtTime 确认 — 刚被命中
            if (!result) {
                int curHurt = mc.thePlayer.hurtTime;
                if (curHurt > 0 && predictPrevHurtTime == 0 && dist <= 4.5) {
                    predictLastAttackTick = mc.thePlayer.ticksExisted;
                    result = true;
                }
            }

            // PATH 3: 冷却预判 — 基于上次攻击时间预测下一次（1.8 剑冷却约 12.5 tick）
            if (!result && predictLastAttackTick > 0) {
                int ticksSince = mc.thePlayer.ticksExisted - predictLastAttackTick;
                if (ticksSince >= 10 && ticksSince <= 14 && facingUs && dist <= 4.0) {
                    result = true;
                }
            }

            // PATH 4: 近距离威胁 — 敌方面对自己、距离极近、刚结束无敌帧
            if (!result && facingUs && dist <= 3.5
                    && target.hurtResistantTime >= 7 && target.hurtResistantTime <= 12) {
                result = true;
            }
        }

        predictPrevHurtTime = mc.thePlayer.hurtTime;
        return result;
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
            return false;
        } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
            if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityLivingBase.deathTime > 0) {
                return false;
            } else if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) {
                return false;
            } else if (!this.throughWalls.getValue() && !RotationUtil.hasVisiblePoint(entityLivingBase.getEntityBoundingBox().expand(entityLivingBase.getCollisionBorderSize(), entityLivingBase.getCollisionBorderSize(), entityLivingBase.getCollisionBorderSize()))) {
                return false;
            } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
                if (!this.players.getValue()) {
                    return false;
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return false;
                } else {
                    return (!this.teams.getValue() || !TeamUtil.isSameTeam((EntityPlayer) entityLivingBase)) && (!this.botCheck.getValue() || !TeamUtil.isBot((EntityPlayer) entityLivingBase));
                }
            } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
                return this.bosses.getValue();
            } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
                if (entityLivingBase instanceof EntityAnimal
                        || entityLivingBase instanceof EntityBat
                        || entityLivingBase instanceof EntitySquid
                        || entityLivingBase instanceof EntityVillager) {
                    return this.animals.getValue();
                } else if (!(entityLivingBase instanceof EntityIronGolem)) {
                    return false;
                } else {
                    return this.golems.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
                }
            } else if (!(entityLivingBase instanceof EntitySilverfish)) {
                return this.mobs.getValue();
            } else {
                return this.silverfish.getValue() && (!this.teams.getValue() || !TeamUtil.hasTeamColor(entityLivingBase));
            }
        } else {
            return false;
        }
    }

    private boolean isInRange(EntityLivingBase entityLivingBase) {
        return this.isInBlockRange(entityLivingBase) || this.isInSwingRange(entityLivingBase) || this.isInAttackRange(entityLivingBase);
    }

    private boolean isInBlockRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.autoBlockRange.getValue();
    }

    private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
    }

    private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
    }

    private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
    }

    private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
        return entityLivingBase instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
    }

    public EntityLivingBase getTarget() {
        return this.target != null ? this.target.getEntity() : null;
    }

    public java.util.List<EntityLivingBase> getTargets() {
        java.util.List<EntityLivingBase> result = new ArrayList<>();
        if (this.target != null && TeamUtil.isEntityLoaded(this.target.getEntity())) {
            result.add(this.target.getEntity());
        }
        if (this.mode.getValue() == 1) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (entity instanceof EntityLivingBase) {
                    EntityLivingBase e = (EntityLivingBase) entity;
                    if (isValidTarget(e) && isInRange(e) && !result.contains(e)) {
                        result.add(e);
                    }
                }
            }
        }
        return result;
    }
    public boolean isOldHypixel() {
        return this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 0;
    }

    public boolean isHypixelWithoutNoSlow() {
        return this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 1;
    }

    public boolean isHypixelCustom() {
        return this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 2;
    }

    public boolean isLag() {
        return this.autoBlock.getValue() == 2 && this.hypixelMode.getValue() == 3;
    }
    public boolean isAttackAllowed() {
        Scaffold scaffold = (Scaffold) Elara.moduleManager.getModule(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        } else {
            return false;
        }
    }

    public boolean shouldAutoBlock() {
        if (this.isPlayerBlocking() && this.isBlocking) {
            return !mc.thePlayer.isInWater() && !mc.thePlayer.isInLava() && (this.autoBlock.getValue() == 2 || this.autoBlock.getValue() == 3 || this.autoBlock.getValue() == 5 || this.autoBlock.getValue() == 6 || this.autoBlock.getValue() == 7);
        } else {
            return false;
        }
    }
    public boolean knockbackCanReduce(int phase, int tick) {
        switch (this.autoBlock.getValue()) {
            case 0:
            case 1:
            case 4:
                return true;
            case 2:
                switch (this.hypixelMode.getValue()) {
                    case 0:
                    case 1:
                        return phase == 2 ? tick == 2 : tick == 0;
                    case 2: {
                        int maxT = Math.max(1, this.maxTick.getValue() - 1);
                        switch (phase) {
                            case 0:
                                return tick == this.attackTick.getValue();
                            case 1:
                                return tick == this.attackTick.getValue() % maxT;
                            default:
                                return tick == (this.attackTick.getValue() - 2 + maxT) % maxT;
                        }
                    }
                    case 3:
                    default:
                        return false;
                }
            case 3:
                return phase == 2 ? tick == 1 : tick == 0;
            default:
                return true;
        }
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.attackDelayMS > 0L) {
                this.attackDelayMS -= 50L;
            }
            boolean attack = this.target != null && this.canAttack();
            boolean block = attack && this.canAutoBlock();
            if (!block) {
                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
                this.blockTick = 0;
            }
            if (attack) {
                boolean swap = false;
                boolean blocked = false;
                if (block) {
                    switch (this.autoBlock.getValue()) {
                        case 0:
                            if (PlayerUtil.isUsingItem()) {
                                this.isBlocking = true;
                                if (!this.isPlayerBlocking() && !Elara.playerStateManager.digging && !Elara.playerStateManager.placing) {
                                    swap = true;
                                }
                            } else {
                                this.isBlocking = false;
                                if (this.isPlayerBlocking() && !Elara.playerStateManager.digging && !Elara.playerStateManager.placing) {
                                    this.stopBlock();
                                }
                            }
                            Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.fakeBlockState = false;
                            break;
                        case 1:
                            if (this.hasValidTarget()) {
                                if (!this.isPlayerBlocking() && !Elara.playerStateManager.digging && !Elara.playerStateManager.placing) {
                                    swap = true;
                                }
                                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 2:
                            if (this.hasValidTarget()) {
                                if (!Elara.playerStateManager.digging && !Elara.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            blocked = true;
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            attack = false;
                                            this.blockTick = 2;
                                            break;
                                        case 2:
                                            if (this.isPlayerBlocking()) {
                                                if (!noSwap.getValue()) {
                                                    int randomSlot = new Random().nextInt(9);
                                                    while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                                        randomSlot = new Random().nextInt(9);
                                                    }
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                                }
                                                this.stopBlock();
                                            }
                                            if (test.getValue()) {
                                                if (testAttackTick >= moreAttackDelay.getValue()) {
                                                    testAttackTick = 0;
                                                } else {
                                                    testAttackTick++;
                                                    attack = false;
                                                }
                                            } else {
                                                attack = false;
                                            }
                                            this.blockTick = 0;
                                            break;
                                        default:
                                            this.blockTick = 0;
                                            break;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                int randomSlot = new Random().nextInt(9);
                                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                    randomSlot = new Random().nextInt(9);
                                }
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 3:
                            if (this.hasValidTarget()) {
                                if (!Elara.playerStateManager.digging && !Elara.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            // Keep attack enabled this tick so when we finish
                                            // the block-start packet we can strike next tick.
                                            break;
                                        case 1:
                                            // Transition to unblock state; attack still enabled
                                            // so on next tick's evaluation we will actually hit.
                                            if (this.isPlayerBlocking()) {
                                                this.stopBlock();
                                            }
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 2;
                                            }
                                            break;
                                        case 2:
                                            // Unblock completed last tick; it's safe to attack.
                                            // Do NOT force attack=false here.
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = this.forceBlockAnim.getValue();
                            } else {
                                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 4:
                            Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = this.hasValidTarget();
                            if (PlayerUtil.isUsingItem()
                                    && !this.isPlayerBlocking()
                                    && !Elara.playerStateManager.digging
                                    && !Elara.playerStateManager.placing) {
                                swap = true;
                            }
                            break;
                        case 5:
                            if (this.hasValidTarget()) {
                                if (!Elara.playerStateManager.digging && !Elara.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            blocked = true;
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (isPlayerBlocking()) {
                                                int randomSlot = new Random().nextInt(9);
                                                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                                    randomSlot = new Random().nextInt(9);
                                                }
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                            }
                                            attack = false;
                                            blockTick = 2;
                                            break;
                                        case 2:
                                            attack = false;
                                            this.stopBlock();
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                int randomSlot = new Random().nextInt(9);
                                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                    randomSlot = new Random().nextInt(9);
                                }
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 6:
                            if (this.hasValidTarget()) {
                                if (!Elara.playerStateManager.digging && !Elara.playerStateManager.placing) {
                                    if (blockTick + 1 == startBlinkTick.getValue()) {
                                        blocked = true;
                                    }
                                    if (blockTick + 1 != attackTick.getValue()) {
                                        attack = false;
                                    }
                                    if (blockTick + 1 == startBlockTick.getValue()) {
                                        if (!this.isPlayerBlocking()) {
                                            swap = true;
                                            if (postStartBlock.getValue()) postBlock = true;
                                        }
                                    }
                                    if (blockTick + 1 == stopBlinkTick.getValue()) {
                                        Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                    }
                                    if (blockTick + 1 == swapTick.getValue()) {
                                        int randomSlot = new Random().nextInt(9);
                                        while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                            randomSlot = new Random().nextInt(9);
                                        }
                                        PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                        swapped = true;
                                    }
                                    if (blockTick + 1 == switchBackTick.getValue()) {
                                        if (swapped) {
                                            PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                            swapped = false;
                                        }
                                    }
                                    if (blockTick + 1 == stopBlockTick.getValue()) {
                                        if (this.isPlayerBlocking()) {
                                            this.stopBlock();
                                        }
                                    }
                                    blockTick++;
                                    if (blockTick >= maxTick.getValue() - 1) {
                                        blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                if (swapped) {
                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                    swapped = false;
                                }
                                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 7:
                            // ──────────────────────────────────────────────────────────
                            // Predict AutoBlock (Rewrite v2 · 三态状态机)
                            //
                            // 0=IDLE       无威胁 → 正常攻击
                            // 1=BLOCKING   预判敌方攻击 → 格挡，不攻击
                            // 2=RECOVER    危险窗口结束 → 释放格挡，允许反击
                            // ──────────────────────────────────────────────────────────
                            if (this.hasValidTarget() && this.target != null) {
                                EntityLivingBase t = this.target.getEntity();
                                boolean incoming = predictEnemyAttack(t);
                                float curSwing = t.swingProgress;

                                if (!Elara.playerStateManager.digging && !Elara.playerStateManager.placing) {
                                    switch (predictState) {
                                        case 0: // IDLE
                                            if (incoming) {
                                                predictState = 1;
                                                predictTick = 0;
                                                if (!this.isPlayerBlocking()) swap = true;
                                                this.isBlocking = true;
                                                attack = false;
                                            } else {
                                                if (this.isPlayerBlocking()) this.stopBlock();
                                                this.isBlocking = false;
                                            }
                                            break;

                                        case 1: // BLOCKING — 格挡中，不攻击
                                            this.isBlocking = true;
                                            attack = false;
                                            predictTick++;
                                            // 危险窗口结束 或 达到最大格挡时间 → 释放
                                            if (!incoming || predictTick >= predictHoldTicks.getValue()) {
                                                predictState = 2;
                                                predictTick = 0;
                                                if (this.isPlayerBlocking()) this.stopBlock();
                                            }
                                            break;

                                        case 2: // RECOVER — 释放格挡，允许反击
                                            this.isBlocking = false;
                                            predictTick++;
                                            if (predictTick >= 1) {
                                                if (incoming) {
                                                    // 仍有威胁 → 重新格挡
                                                    predictState = 1;
                                                    predictTick = 0;
                                                    if (!this.isPlayerBlocking()) swap = true;
                                                    this.isBlocking = true;
                                                    attack = false;
                                                } else {
                                                    predictState = 0;
                                                    predictTick = 0;
                                                }
                                            }
                                            break;
                                    }
                                } else {
                                    this.isBlocking = false;
                                }

                                this.fakeBlockState = this.forceBlockAnim.getValue();
                                predictPrevSwing = curSwing;
                                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            } else {
                                predictState = 0;
                                predictTick = 0;
                                predictPrevSwing = 0.0f;
                                predictPrevHurtTime = 0;
                                predictLastAttackTick = -1;
                                if (this.isPlayerBlocking()) this.stopBlock();
                                Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                    }
                }
                boolean attacked = false;
                if (this.isBoxInSwingRange(this.target.getBox())) {
                    if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {
                        float[] rotations = RotationUtil.getRotationsToBox(
                                this.target.getBox(),
                                event.getYaw(),
                                event.getPitch(),
                                (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F),
                                (float) this.smoothing.getValue() / 100.0F
                        );
                        event.setRotation(rotations[0], rotations[1], 1);
                        if (this.rotations.getValue() == 3) {
                            Elara.rotationManager.setRotation(rotations[0], rotations[1], 1, true);
                        }
                        if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) {
                            event.setPervRotation(rotations[0], 1);
                        }
                    }
                    if (attack) {
                        attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
                    }
                }
                if (swap) {
                    if (attacked) {
                        this.interactAttack(event.getNewYaw(), event.getNewPitch());
                    } else {
                        if (!postBlock) this.sendUseItem();
                    }
                }
                if (blocked) {
                    Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    Elara.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                }
            }
        }
        if (event.getType() == EventType.POST && this.isEnabled()) {
            if (postSwap) {
                int randomSlot = new Random().nextInt(9);
                while (randomSlot == mc.thePlayer.inventory.currentItem) {
                    randomSlot = new Random().nextInt(9);
                }
                PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload("send", new PacketBuffer(Unpooled.buffer())));
                PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                this.stopBlock();
                postSwap = false;
            }
            if (postBlock) {
                sendUseItem();
                postBlock = false;
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            switch (event.getType()) {
                case PRE:
                    if (this.target == null
                            || !this.isValidTarget(this.target.getEntity())
                            || !this.isBoxInAttackRange(this.target.getBox())
                            || !this.isBoxInSwingRange(this.target.getBox())
                            || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
                        this.timer.reset();
                        ArrayList<EntityLivingBase> targets = new ArrayList<>();
                        for (Entity entity : mc.theWorld.loadedEntityList) {
                            if (entity instanceof EntityLivingBase
                                    && this.isValidTarget((EntityLivingBase) entity)
                                    && this.isInRange((EntityLivingBase) entity)) {
                                targets.add((EntityLivingBase) entity);
                            }
                        }
                        if (targets.isEmpty()) {
                            this.target = null;
                        } else {
                            if (targets.stream().anyMatch(this::isInSwingRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInSwingRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isInAttackRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInAttackRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isPlayerTarget)) {
                                targets.removeIf(entityLivingBase -> !this.isPlayerTarget(entityLivingBase));
                            }
                            targets.sort(
                                    (entityLivingBase1, entityLivingBase2) -> {
                                        int sortBase = 0;
                                        switch (this.sort.getValue()) {
                                            case 1:
                                                sortBase = Float.compare(TeamUtil.getHealthScore(entityLivingBase1), TeamUtil.getHealthScore(entityLivingBase2));
                                                break;
                                            case 2:
                                                sortBase = Integer.compare(entityLivingBase1.hurtResistantTime, entityLivingBase2.hurtResistantTime);
                                                break;
                                            case 3:
                                                sortBase = Float.compare(
                                                        RotationUtil.angleToEntity(entityLivingBase1),
                                                        RotationUtil.angleToEntity(entityLivingBase2)
                                                );
                                        }
                                        return sortBase != 0
                                                ? sortBase
                                                : Double.compare(RotationUtil.distanceToEntity(entityLivingBase1), RotationUtil.distanceToEntity(entityLivingBase2));
                                    }
                            );
                            if (this.mode.getValue() == 1 && this.hitRegistered) {
                                this.hitRegistered = false;
                                this.switchTick++;
                            }
                            if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) {
                                this.switchTick = 0;
                            }
                            this.target = new AttackData(targets.get(this.switchTick));
                        }
                    }
                    if (this.target != null) {
                        this.target = new AttackData(this.target.getEntity());
                    }
                    break;
                case POST:
                    if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
                        mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                    }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if (event.getPacket() instanceof C07PacketPlayerDigging) {
                C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
                if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                    this.blockingState = false;
                }
            }
            if (event.getPacket() instanceof C09PacketHeldItemChange) {
                this.blockingState = false;
                if (this.isBlocking) {
                    mc.thePlayer.stopUsingItem();
                }
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && this.rotations.getValue() != 3
                    && RotationState.isActived()
                    && RotationState.getPriority() == 1.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && target != null) {
            if (this.showTarget.getValue() != 0
                    && TeamUtil.isEntityLoaded(this.target.getEntity())
                    && this.isAttackAllowed()) {
                Color color = new Color(-1);
                switch (this.showTarget.getValue()) {
                    case 1:
                        if (this.target.getEntity().hurtTime > 0) {
                            color = new Color(16733525);
                        } else {
                            color = new Color(5635925);
                        }
                        break;
                    case 2:
                        color = ((HUD) Elara.moduleManager.getModule(HUD.class)).getColor(System.currentTimeMillis());
                }
                RenderUtil.enableRenderState();
                RenderUtil.drawEntityBox(this.target.getEntity(), color.getRed(), color.getGreen(), color.getBlue());
                RenderUtil.disableRenderState();
            }
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && this.target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        this.target = null;
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMS = 0L;
        this.blockTick = 0;
    }

    @Override
    public void onDisabled() {
        Elara.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.blockingState = false;
        this.isBlocking = false;
        this.fakeBlockState = false;
        this.predictState = 0;
        this.predictTick = 0;
        this.predictPrevSwing = 0.0f;
        this.predictPrevHurtTime = 0;
        this.predictLastAttackTick = -1;
    }

    @Override
    public void verifyValue(String mode) {
        if (!this.autoBlock.getName().equals(mode) && !this.autoBlockCPS.getName().equals(mode)) {
            if (this.swingRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.attackRange.setValue(this.swingRange.getValue());
                }
            } else if (this.attackRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.swingRange.setValue(this.attackRange.getValue());
                }
            } else if (this.minCPS.getName().equals(mode)) {
                if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.maxCPS.setValue(this.minCPS.getValue());
                }
            } else {
                if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.minCPS.setValue(this.maxCPS.getValue());
                }
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    public static class AttackData {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;

        public AttackData(EntityLivingBase entityLivingBase) {
            this.entity = entityLivingBase;
            double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
            this.box = entityLivingBase.getEntityBoundingBox().expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
            this.x = entityLivingBase.posX;
            this.y = entityLivingBase.posY;
            this.z = entityLivingBase.posZ;
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}