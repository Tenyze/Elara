package elara.util.rotation;

import elara.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

import java.util.Random;

/**
 * 共用人类式旋转平滑内核。
 * <p>
 * 物理模型：加速度(accel) + 阻尼(damping) + 接近减速(approachDecel) + 微过冲(overshoot)。
 * GCD 对齐：按 Minecraft 鼠标灵敏度的量化步长对 step 取模，保证服务器端看不出是合成旋转。
 * 双缓冲插值：Tick 计算 sentYaw/sentPitch（给 UpdateEvent 发包），Render 时按 partialTicks 插值
 *           prevSent -> sent，返回给视觉渲染用，无鼠标拉扯感。
 * <p>
 * 每个需要转头的模块（KillAura / AimAssist）各自 new 一个实例，冲突靠使用前判断
 * RotationState priority 或模块优先级自行解决，内核不处理跨模块仲裁。
 */
public class RotationKernel {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random rand = new Random();

    // ===== 双缓冲（上一帧发送值 -> 本帧发送值）=====
    private float prevSentYaw;
    private float prevSentPitch;
    private float sentYaw;
    private float sentPitch;

    // ===== 物理速度状态 =====
    private float velYaw;
    private float velPitch;

    // ===== 参数（外部可调，每 tick 可以改）=====
    private float maxSpeedDeg = 85f;      // 最大角速度 (deg/tick)，85 ≈ 1700°/s 超高速锁头
    private float accel = 0.55f;          // 加速度系数（0.55 约 2 tick 达到期望速度 90%）
    private float damp = 0.97f;           // 每 tick 速度阻尼（0.97 稳态≈期望速度 94%，几乎满速滑行）
    private float approachPower = 1.20f;  // 接近减速指数（1.2 = 轻微减速，避免最后阶段磨叽）
    private float jitterBase = 0.015f;    // 基础抖动
    private float jitterScale = 0.5f;     // 距离抖动缩放（deg，按目标距离动态）
    private float overshoot = 0.018f;     // 过冲系数（略降低，避免高速超调）
    private int predictTicks = 0;         // 目标运动预判 tick 数
    private boolean enableGcd = true;     // 是否启用 GCD 对齐

    // ===== 目标 & 状态 =====
    private boolean hasTarget;
    private float lastDiffYaw;
    private float lastDiffPitch;

    public RotationKernel() {
        this.reset(Float.NaN, Float.NaN);
    }

    /**
     * 重置内核状态到给定初始 yaw/pitch（模块刚启用或切目标时调用）。
     * 传 NaN 表示用 mc.thePlayer 当前视角。
     * 超大数据/NaN 会被过滤：脏数据时一律回落到玩家当前角度，避免一次脏输入直接让内核锁死。
     */
    public void reset(float initYaw, float initPitch) {
        // —— 脏数据过滤（NaN / Inf / |yaw| > 10000°）——
        boolean yDirty = !Float.isFinite(initYaw) || Math.abs(initYaw) > 10000f;
        boolean pDirty = !Float.isFinite(initPitch) || Math.abs(initPitch) > 1000f;
        float y = (Float.isNaN(initYaw) || yDirty) ? (mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0f) : initYaw;
        float p = (Float.isNaN(initPitch) || pDirty) ? (mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0f) : initPitch;
        this.prevSentYaw = this.sentYaw = MathHelper.wrapAngleTo180_float(y);
        this.prevSentPitch = this.sentPitch = MathHelper.clamp_float(p, -90f, 90f);
        this.velYaw = 0f;
        this.velPitch = 0f;
        this.hasTarget = false;
        this.lastDiffYaw = 0f;
        this.lastDiffPitch = 0f;
    }

    // ============ 参数设置（链式调用也行） ============
    public void setMaxSpeed(float degPerTick) { this.maxSpeedDeg = Math.max(1f, degPerTick); }
    public void setAccel(float a) { this.accel = MathHelper.clamp_float(a, 0.001f, 0.99f); }
    public void setDamp(float d) { this.damp = MathHelper.clamp_float(d, 0.5f, 0.995f); }
    public void setApproachPower(float p) { this.approachPower = MathHelper.clamp_float(p, 0.5f, 5f); }
    public void setJitter(float base, float scaleByDist) { this.jitterBase = Math.max(0f, base); this.jitterScale = Math.max(0f, scaleByDist); }
    public void setOvershoot(float o) { this.overshoot = MathHelper.clamp_float(o, 0f, 0.15f); }
    public void setPredictTicks(int t) { this.predictTicks = MathHelper.clamp_int(t, 0, 5); }
    public void setEnableGcd(boolean b) { this.enableGcd = b; }

    /** 直接锁定到目标角度（instant lock 开关用：不做物理插值直接跳过去，肉眼可见快） */
    public void setSent(float yaw, float pitch) {
        // 脏数据直接拒绝：NaN / Inf / 超大角度 → 本次调用不修改任何状态，保 sent/prev/vel 不变
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(yaw) > 10000f || Math.abs(pitch) > 1000f) {
            return;
        }
        this.prevSentYaw = this.sentYaw;
        this.prevSentPitch = this.sentPitch;
        this.sentYaw = MathHelper.wrapAngleTo180_float(yaw);
        this.sentPitch = MathHelper.clamp_float(pitch, -90f, 90f);
        this.velYaw *= 0.05f; // 清除惯性避免下 tick 被带飞
        this.velPitch *= 0.05f;
    }

    // ============ 对外读取 sent 值（用于 UpdateEvent.setRotation 发包） ============
    public float getSentYaw() { return MathHelper.wrapAngleTo180_float(sentYaw); }
    public float getSentPitch() { return MathHelper.clamp_float(sentPitch, -90f, 90f); }
    public float getPrevSentYaw() { return MathHelper.wrapAngleTo180_float(prevSentYaw); }
    public float getPrevSentPitch() { return MathHelper.clamp_float(prevSentPitch, -90f, 90f); }
    public boolean hasTarget() { return hasTarget; }

    /**
     * 视觉插值：Render3DEvent 里用，返回当前 partialTicks 下的视觉 yaw/pitch。
     * AimAssist 直接把返回值写 mc.thePlayer.rotationYaw/Pitch 就行。
     */
    public float[] getVisual(float partialTicks) {
        float p = MathHelper.clamp_float(partialTicks, 0f, 1f);
        float y = prevSentYaw + RotationUtil.normalizeAngle(sentYaw - prevSentYaw) * p;
        float pitch = prevSentPitch + (sentPitch - prevSentPitch) * p;
        return new float[]{ MathHelper.wrapAngleTo180_float(y), MathHelper.clamp_float(pitch, -90f, 90f) };
    }

    // ============ 核心：Tick 级 step（UpdateEvent.PRE 调一次） ============

    /**
     * 朝目标旋转一步，更新内部 sentYaw/sentPitch。
     *
     * @param targetYawDeg 目标 yaw（度）
     * @param targetPitchDeg 目标 pitch（度）
     * @param targetDistance 目标距离（用于动态 jitter，<=0 表示忽略）
     * @param rangeMax 最大攻击距离（用于归一化距离，<=0 用默认 4.5）
     * @return true 表示已到达目标（角度差 < 0.05 deg）
     */
    public boolean step(float targetYawDeg, float targetPitchDeg, double targetDistance, double rangeMax) {
        // —— 脏数据前置拦截：NaN / Inf / 超大角度 → 不推进，保留上一帧状态，hasTarget 清掉避免假"到达"
        if (!Float.isFinite(targetYawDeg) || !Float.isFinite(targetPitchDeg)
                || Math.abs(targetYawDeg) > 10000f || Math.abs(targetPitchDeg) > 1000f) {
            this.hasTarget = false;
            return false;
        }

        // 存档 prev
        this.prevSentYaw = this.sentYaw;
        this.prevSentPitch = this.sentPitch;

        float targetY = MathHelper.wrapAngleTo180_float(targetYawDeg);
        float targetP = MathHelper.clamp_float(targetPitchDeg, -90f, 90f);

        // 归一化角度差
        float dY = RotationUtil.normalizeAngle(targetY - this.sentYaw);
        float dP = targetP - this.sentPitch;

        this.hasTarget = true;

        // 到达判定
        if (Math.abs(dY) < 0.05f && Math.abs(dP) < 0.05f) {
            this.sentYaw = targetY;
            this.sentPitch = targetP;
            this.velYaw *= 0.2f;
            this.velPitch *= 0.2f;
            this.lastDiffYaw = dY;
            this.lastDiffPitch = dP;
            return true;
        }

        // 1) 接近减速：距离越近减速越强（Ease-out，pow(x, approachPower) 当 p>1 时才是真·减速）
        //    注意：这里必须是 pow(x, approachPower)，不是 pow(x, 1/p)！前者让 nearF 随接近而缩小→真减速，
        //    后者会让 nearF 接近目标时变大→加速接近→必然超调+来回震荡。
        double absDY = Math.abs(dY);
        double absDP = Math.abs(dP);
        float nearF = 1f;
        if (absDY < 12.0) nearF *= Math.pow((float) absDY / 12.0f, this.approachPower);
        if (absDP < 10.0) nearF *= Math.pow((float) absDP / 10.0f, this.approachPower);
        // 最低 15% 期望速度上限兜底，避免最后 1 度蠕行半天到不了
        nearF = Math.max(nearF, 0.15f);

        // 2) 期望速度（接近目标 * 限速）
        float desiredVy = Math.signum(dY) * Math.min(Math.abs(dY), this.maxSpeedDeg) * nearF;
        float desiredVp = Math.signum(dP) * Math.min(Math.abs(dP), this.maxSpeedDeg * 0.85f) * nearF;

        // 3) 加速度：当前 vel 向 desiredVel 推进
        this.velYaw += (desiredVy - this.velYaw) * this.accel;
        this.velPitch += (desiredVp - this.velPitch) * this.accel;

        // 4) 阻尼（模拟惯性衰减）
        this.velYaw *= this.damp;
        this.velPitch *= this.damp;

        // 5) 微过冲：如果上一帧跟这一帧差方向没变、接近末尾，给一点冲量再回弹
        if (Math.abs(dY) < 4.5f && Math.signum(dY) == Math.signum(this.lastDiffYaw)) {
            this.velYaw += Math.signum(dY) * this.overshoot * Math.abs(dY);
        }
        if (Math.abs(dP) < 3.5f && Math.signum(dP) == Math.signum(this.lastDiffPitch)) {
            this.velPitch += Math.signum(dP) * this.overshoot * Math.abs(dP);
        }
        this.lastDiffYaw = dY;
        this.lastDiffPitch = dP;

        // 6) 最大速度硬封顶
        if (Math.abs(this.velYaw) > this.maxSpeedDeg) this.velYaw = Math.signum(this.velYaw) * this.maxSpeedDeg;
        if (Math.abs(this.velPitch) > this.maxSpeedDeg * 0.85f) this.velPitch = Math.signum(this.velPitch) * this.maxSpeedDeg * 0.85f;

        // 7) GCD 对齐：跟玩家鼠标灵敏度量化到同一阶梯，否则反作弊一眼假
        float stepY = this.velYaw;
        float stepP = this.velPitch;
        if (this.enableGcd) {
            float gcd = computeGcd();
            if (gcd > 0.0001f) {
                stepY = stepY - (float) Math.IEEEremainder(stepY, gcd);
                stepP = stepP - (float) Math.IEEEremainder(stepP, gcd);
            }
        }

        // 8) 距离动态抖动（近处大，远处小；边界 hysteresis 0.4 block 留给调用方处理）
        float distNorm = 0.5f;
        if (targetDistance > 0.0 && rangeMax > 0.0) {
            distNorm = (float) MathHelper.clamp_double(targetDistance / rangeMax, 0.05, 1.0);
        }
        // 越接近最大距离，jitter 越大（最多 150%）；越近越小（最少 40%）
        float jFactor = 0.4f + distNorm * 1.1f;
        float jY = (rand.nextFloat() - 0.5f) * this.jitterBase * jFactor;
        float jP = (rand.nextFloat() - 0.5f) * this.jitterBase * jFactor * 0.65f;

        // 9) 应用 step
        this.sentYaw = MathHelper.wrapAngleTo180_float(this.sentYaw + stepY + jY);
        this.sentPitch = MathHelper.clamp_float(this.sentPitch + stepP + jP, -90f, 90f);

        // 10) 最后防止超调过了头：如果 step 后越过目标且步长 < 0.15 度则吸附
        float newDY = RotationUtil.normalizeAngle(targetY - this.sentYaw);
        float newDP = targetP - this.sentPitch;
        if (Math.signum(newDY) != Math.signum(dY) && Math.abs(newDY) < 0.15f) this.sentYaw = targetY;
        if (Math.signum(newDP) != Math.signum(dP) && Math.abs(newDP) < 0.15f) this.sentPitch = targetP;

        return false;
    }

    /** 不依赖距离的简化版 step（AimAssist 等场景，distance 传 0 即可） */
    public boolean step(float targetYawDeg, float targetPitchDeg) {
        return step(targetYawDeg, targetPitchDeg, 0.0, 0.0);
    }

    /** 取消目标（松手 / 丢失目标）：把 sent 拉回玩家真实视角，做一个平滑还原 */
    public void releaseToPlayer(float playerYaw, float playerPitch) {
        this.prevSentYaw = this.sentYaw;
        this.prevSentPitch = this.sentPitch;
        float dY = RotationUtil.normalizeAngle(playerYaw - this.sentYaw);
        float dP = playerPitch - this.sentPitch;
        // 直接走一步强阻尼，用内核物理模型还原
        float desiredVy = Math.signum(dY) * Math.min(Math.abs(dY), this.maxSpeedDeg * 0.6f);
        float desiredVp = Math.signum(dP) * Math.min(Math.abs(dP), this.maxSpeedDeg * 0.55f);
        this.velYaw += (desiredVy - this.velYaw) * 0.09f;
        this.velPitch += (desiredVp - this.velPitch) * 0.09f;
        this.velYaw *= 0.85f;
        this.velPitch *= 0.85f;
        if (this.enableGcd) {
            float g = computeGcd();
            if (g > 0.0001f) {
                this.velYaw = this.velYaw - (float) Math.IEEEremainder(this.velYaw, g);
                this.velPitch = this.velPitch - (float) Math.IEEEremainder(this.velPitch, g);
            }
        }
        this.sentYaw = MathHelper.wrapAngleTo180_float(this.sentYaw + this.velYaw);
        this.sentPitch = MathHelper.clamp_float(this.sentPitch + this.velPitch, -90f, 90f);
        this.hasTarget = false;
    }

    // ============ 辅助：GCD 计算（1.8 Minecraft 鼠标灵敏度量化步长） ============
    private float computeGcd() {
        try {
            // Minecraft 原版：每帧鼠标位移 dx/dy 会乘 sensitivity 后再乘 0.15，再 +0.2 得到 gcd
            // gcd = (sens * 0.6 + 0.2)^3 * 8 （近似）
            float sens = 0.5f;
            if (mc.gameSettings != null) {
                sens = mc.gameSettings.mouseSensitivity;
            }
            float f = sens * 0.6f + 0.2f;
            return (float) (f * f * f * 8.0 * 0.15D);
        } catch (Throwable ignored) {
            return 0.05f;
        }
    }
}
