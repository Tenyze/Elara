package elara.module.combat;

import elara.Elara;
import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.events.KeyEvent;
import elara.events.LeftClickMouseEvent;
import elara.events.TickEvent;
import elara.module.Module;
import elara.util.*;
import elara.property.properties.BooleanProperty;
import elara.property.properties.FloatProperty;
import elara.property.properties.PercentProperty;
import elara.property.properties.IntProperty;
import elara.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();

    // ========== 瞄准部位 ==========
    // HEAD: 顶部(1.0) / CHEST: 胸部(0.75) / BODY: 身体(0.5) / LEGS: 腿部(0.25) / AUTO: 自适应
    private static final int TARGET_HEAD = 0;
    private static final int TARGET_CHEST = 1;
    private static final int TARGET_BODY = 2;
    private static final int TARGET_LEGS = 3;
    private static final int TARGET_AUTO = 4;

    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 6.0F, 0.0F, 20.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 5.0F, 0.0F, 20.0F);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 50);
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 8.0F);
    public final IntProperty fov = new IntProperty("fov", 90, 30, 360);
    public final ModeProperty targetPoint = new ModeProperty("target-point", TARGET_CHEST,
            new String[]{"Head", "Chest", "Body", "Legs", "Auto"});
    public final BooleanProperty weaponOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponOnly::getValue);
    public final BooleanProperty botChecks = new BooleanProperty("bot-check", true);
    public final BooleanProperty team = new BooleanProperty("teams", true);

    // 点按左键适配：当用户刚点击左键时暂停 1 tick，避免手动瞄准与自瞄叠加卡顿
    private int clickSkipTicks = 0;

    public AimAssist() {
        super("AimAssist", false);

        hSpeed.setCategory("Rotation");
        vSpeed.setCategory("Rotation");
        smoothing.setCategory("Rotation");
        targetPoint.setCategory("Rotation");
        range.setCategory("Targeting");
        fov.setCategory("Targeting");
        weaponOnly.setCategory("Conditions");
        allowTools.setCategory("Conditions");
        botChecks.setCategory("Targeting");
        team.setCategory("Targeting");
    }

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer == mc.thePlayer || entityPlayer == mc.thePlayer.ridingEntity) return false;
        if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) return false;
        if (entityPlayer.deathTime > 0) return false;
        if (RotationUtil.distanceToEntity(entityPlayer) > (double) this.range.getValue()) return false;
        if (RotationUtil.angleToEntity(entityPlayer) > (float) this.fov.getValue()) return false;
        float border = entityPlayer.getCollisionBorderSize();
        AxisAlignedBB box = entityPlayer.getEntityBoundingBox().expand(border, border, border);
        if (!hasAnyVisibleSegment(box)) return false;
        if (TeamUtil.isFriend(entityPlayer)) return false;
        return (!this.team.getValue() || !TeamUtil.isSameTeamAdvanced(entityPlayer))
                && (!this.botChecks.getValue() || !TeamUtil.isBot(entityPlayer));
    }

    private boolean isInReach(EntityPlayer entityPlayer) {
        Reach reach = (Reach) Elara.moduleManager.getModule(Reach.class);
        double distance = reach != null && reach.isEnabled() ? (double) reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(entityPlayer) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    /**
     * 对 box 做 4/4 段扫描，只要任意一段有可见点就返回 true（用于 isValidTarget）。
     */
    private boolean hasAnyVisibleSegment(AxisAlignedBB box) {
        double[] heights = {0.90, 0.75, 0.50, 0.25};
        for (double h : heights) {
            double cy = box.minY + (box.maxY - box.minY) * h;
            AxisAlignedBB probe = new AxisAlignedBB(
                    box.minX, box.minY + (box.maxY - box.minY) * (h - 0.12),
                    box.maxX, box.minY + (box.maxY - box.minY) * (h + 0.12),
                    box.minZ, box.maxZ
            );
            if (RotationUtil.hasVisiblePoint(probe)) return true;
        }
        return RotationUtil.hasVisiblePoint(box);
    }

    /**
     * 根据 targetPoint 模式选择最佳瞄准点，返回世界坐标 Vec3。
     * AUTO 模式下在 4/4 段中选择角度差最小且可见的点，胸部优先。
     */
    private Vec3 pickTargetPoint(AxisAlignedBB box, float currentYaw, float currentPitch) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double h = box.maxY - box.minY;

        int mode = this.targetPoint.getValue();
        double[] heights;
        switch (mode) {
            case TARGET_HEAD:
                heights = new double[]{0.90};
                break;
            case TARGET_CHEST:
                heights = new double[]{0.75, 0.90, 0.50, 0.25}; // 胸部优先，不可见时降级
                break;
            case TARGET_BODY:
                heights = new double[]{0.50, 0.75, 0.25, 0.90};
                break;
            case TARGET_LEGS:
                heights = new double[]{0.25, 0.50, 0.75, 0.90};
                break;
            case TARGET_AUTO:
            default:
                heights = new double[]{0.75, 0.90, 0.50, 0.25}; // 胸部优先，自动换段
                break;
        }

        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 bestPoint = null;
        float bestCost = Float.MAX_VALUE;

        for (double heightRatio : heights) {
            double cy = box.minY + h * heightRatio;
            // 每个段内扫描 3x3 点，取最优
            for (int ix = 0; ix < 3; ix++) {
                for (int iz = 0; iz < 3; iz++) {
                    double px = box.minX + (box.maxX - box.minX) * (0.25 + ix * 0.25);
                    double pz = box.minZ + (box.maxZ - box.minZ) * (0.25 + iz * 0.25);
                    Vec3 candidate = new Vec3(px, cy, pz);

                    // 可见性检查（快速）
                    MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eye, candidate, false, true, false);
                    if (mop != null) {
                        double blockDist = eye.distanceTo(mop.hitVec);
                        double candDist = eye.distanceTo(candidate);
                        if (blockDist < candDist - 1e-4) continue; // 被方块挡
                    }

                    float[] rot = RotationUtil.getRotationsTo(candidate.xCoord, candidate.yCoord, candidate.zCoord,
                            currentYaw, currentPitch);
                    float dyaw = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - currentYaw));
                    float dpitch = Math.abs(rot[1] - currentPitch);
                    float cost = dyaw + dpitch * 1.5f; // 垂直权重略高，避免频繁抬头低头

                    if (cost < bestCost) {
                        bestCost = cost;
                        bestPoint = candidate;
                    }
                }
            }
            // 如果不是 AUTO 模式，选中第一个 heights 内最佳点即退出（不降级）
            if (mode != TARGET_AUTO && bestPoint != null) return bestPoint;
        }
        if (bestPoint != null) return bestPoint;
        // 全部失败回退到中心
        return new Vec3(cx, box.minY + h * 0.75, cz);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST || mc.currentScreen != null) {
            return;
        }

        if (clickSkipTicks > 0) {
            clickSkipTicks--;
            return;
        }

        if (!(Boolean) this.weaponOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || (this.allowTools.getValue() && ItemUtil.isHoldingTool())) {
            boolean attacking = PlayerUtil.isAttacking();
            if (attacking && this.isLookingAtBlock()) {
                return;
            }

            if (!attacking && this.timer.hasTimeElapsed(350L)) {
                return;
            }

            List<EntityPlayer> inRange = mc.theWorld
                    .loadedEntityList
                    .stream()
                    .filter(entity -> entity instanceof EntityPlayer)
                    .map(entity -> (EntityPlayer) entity)
                    .filter(this::isValidTarget)
                    .sorted(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                    .collect(Collectors.toList());

            if (inRange.isEmpty()) {
                return;
            }

            if (inRange.stream().anyMatch(this::isInReach)) {
                inRange.removeIf(entityPlayer -> !this.isInReach(entityPlayer));
            }

            EntityPlayer target = inRange.get(0);
            if (RotationUtil.distanceToEntity(target) <= 0.0) {
                return;
            }

            float currentYaw = mc.thePlayer.rotationYaw;
            float currentPitch = mc.thePlayer.rotationPitch;

            // 过滤 NaN / 超大角度脏数据
            if (Float.isNaN(currentYaw) || Float.isNaN(currentPitch)) return;
            if (Math.abs(currentYaw) > 720.0f || Math.abs(currentPitch) > 270.0f) return;

            AxisAlignedBB box = target.getEntityBoundingBox();
            double collisionBorderSize = target.getCollisionBorderSize();
            box = box.expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);

            // 1) 选定瞄准点（按 targetPoint 模式）
            Vec3 aimPoint = pickTargetPoint(box, currentYaw, currentPitch);

            // 2) 计算目标旋转（不带 smoothFactor，这里我们手动吸附 + 平滑）
            float[] targetRot = RotationUtil.getRotationsTo(aimPoint.xCoord, aimPoint.yCoord, aimPoint.zCoord,
                    currentYaw, currentPitch, 180.0F, 0.0F);
            float targetYaw = targetRot[0];
            float targetPitch = targetRot[1];

            // 3) 计算角度差，应用最短路径
            float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw);
            float deltaPitch = MathHelper.wrapAngleTo180_float(targetPitch - currentPitch);

            // 4) 吸附逻辑：角度越大加速因子越高；小角度做微修正
            float absYaw = Math.abs(deltaYaw);
            float absPitch = Math.abs(deltaPitch);
            float fovVal = (float) this.fov.getValue();
            float yawThreshold = fovVal * 0.6f; // 超过此阈值进入"强力吸附区"

            double yawMultiplier = 1.0;
            double pitchMultiplier = 1.0;
            if (absYaw > yawThreshold) {
                // 非线性加速：(diff/threshold)^1.5，最大约 2.5x
                yawMultiplier = Math.pow(absYaw / Math.max(yawThreshold, 1.0f), 1.5);
                yawMultiplier = Math.max(1.0, Math.min(yawMultiplier, 2.5));
            }
            if (absPitch > 30.0f) {
                pitchMultiplier = Math.pow(absPitch / 30.0f, 1.3);
                pitchMultiplier = Math.max(1.0, Math.min(pitchMultiplier, 2.2));
            }
            // 小角度微修正降速，防抖动
            if (absYaw < 5.0f) yawMultiplier *= 0.6f;
            if (absPitch < 3.0f) pitchMultiplier *= 0.5f;

            // 5) 基础步长计算：speed 与 smoothing 共同作用
            //    原始公式：0.1F * yawSpeed 作为比例，换成更直观的 step 控制
            float yawSpeedVal = Math.min(Math.abs(this.hSpeed.getValue()), 20.0F);
            float pitchSpeedVal = Math.min(Math.abs(this.vSpeed.getValue()), 20.0F);
            // smoothing=0 → 线性步进，smoothing=100 → 极低步进
            float smoothFactor = (float) this.smoothing.getValue() / 100.0F;
            float baseYawStep = Math.max(0.1f, yawSpeedVal * (1.0f - 0.5f * smoothFactor));
            float basePitchStep = Math.max(0.05f, pitchSpeedVal * (1.0f - 0.5f * smoothFactor));

            float finalYawStep = (float) (baseYawStep * yawMultiplier);
            float finalPitchStep = (float) (basePitchStep * pitchMultiplier);

            // 6) 应用最短路径 + clamp 步进
            if (Math.abs(deltaYaw) > finalYawStep) {
                deltaYaw = Math.signum(deltaYaw) * finalYawStep;
            }
            if (Math.abs(deltaPitch) > finalPitchStep) {
                deltaPitch = Math.signum(deltaPitch) * finalPitchStep;
            }

            float newYaw = currentYaw + deltaYaw;
            float newPitch = MathHelper.clamp_float(currentPitch + deltaPitch, -90.0F, 90.0F);

            // 过滤输出 NaN
            if (Float.isNaN(newYaw) || Float.isNaN(newPitch)) return;

            // 7) 写入 RotationManager，priority=0（与原代码一致）
            Elara.rotationManager.setRotation(newYaw, newPitch, 0, false);
        }
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode()
                && !Elara.moduleManager.getModule(elara.module.utility.AutoClicker.class).isEnabled()) {
            this.timer.reset();
            // 点按左键适配：刚按下左键时暂停 1 tick 自瞄，防止手动单次瞄准与自瞄叠加卡顿
            clickSkipTicks = 1;
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        // AutoClicker 未启用且是单次点按时跳过一帧自瞄（配合 onPress）
        if (!Elara.moduleManager.getModule(elara.module.utility.AutoClicker.class).isEnabled()) {
            clickSkipTicks = Math.max(clickSkipTicks, 1);
        }
    }
}
