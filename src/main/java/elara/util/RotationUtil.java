package elara.util;

import elara.mixin.IAccessorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import java.util.Random;

public final class RotationUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random rand = new Random();
    private RotationUtil() {}

    public static float normalizeAngle(float angle) {
        return MathHelper.wrapAngleTo180_float(angle);
    }

    public static float getAngleDifference(float[] a, float[] b) {
        float dy = normalizeAngle(a[0] - b[0]);
        float dp = normalizeAngle(a[1] - b[1]);
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    public static float getAngleDifference(float yaw1, float pitch1, float yaw2, float pitch2) {
        float dy = normalizeAngle(yaw1 - yaw2);
        float dp = normalizeAngle(pitch1 - pitch2);
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    public static boolean isFacingEntity(Entity entity, float maxAngle) {
        return angleToEntity(entity) <= maxAngle;
    }

    public static double distanceToEntity(Entity entity) {
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
        return distanceToBox(box);
    }

    public static double distanceToBox(AxisAlignedBB box) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        return distanceToBoxFromPoint(box, eye);
    }

    public static double distanceToBoxFromPoint(AxisAlignedBB box, Vec3 point) {
        if (box.isVecInside(point)) {
            return 0.0;
        }
        Vec3 clamped = clampPointToBox(point, box);
        double dx = clamped.xCoord - point.xCoord;
        double dy = clamped.yCoord - point.yCoord;
        double dz = clamped.zCoord - point.zCoord;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static Vec3 clampPointToBox(Vec3 point, AxisAlignedBB box) {
        double x = MathHelper.clamp_double(point.xCoord, box.minX, box.maxX);
        double y = MathHelper.clamp_double(point.yCoord, box.minY, box.maxY);
        double z = MathHelper.clamp_double(point.zCoord, box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    public static Vec3 clampVecToBox(Vec3 vector, AxisAlignedBB boundingBox) {
        return clampPointToBox(vector, boundingBox);
    }

    public static boolean hasVisiblePoint(AxisAlignedBB box) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        if (box.isVecInside(eye)) {
            return true;
        }
        double w = (box.maxX - box.minX) * 0.5;
        double h = (box.maxY - box.minY) * 0.5;
        double d = (box.maxZ - box.minZ) * 0.5;
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double[][] pts = {
                {cx, cy, cz},
                {cx - w, box.minY, cz - d}, {cx + w, box.minY, cz - d},
                {cx - w, box.minY, cz + d}, {cx + w, box.minY, cz + d},
                {cx - w, box.maxY, cz - d}, {cx + w, box.maxY, cz - d},
                {cx - w, box.maxY, cz + d}, {cx + w, box.maxY, cz + d},
                {cx, box.minY, cz - d}, {cx, box.minY, cz + d},
                {cx, cy + h, cz}, {cx, cy, cz - d}
        };
        for (double[] p : pts) {
            Vec3 target = new Vec3(p[0], p[1], p[2]);
            MovingObjectPosition boxHit = box.calculateIntercept(eye, target);
            double boxDist = (boxHit != null && boxHit.hitVec != null) ? eye.distanceTo(boxHit.hitVec) : eye.distanceTo(target);
            MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eye, target);
            if (mop == null) {
                return true;
            }
            double blockDist = eye.distanceTo(mop.hitVec);
            if (blockDist >= boxDist - 1.0E-4) {
                return true;
            }
        }
        return false;
    }

    public static float[] getRotationsToBox(AxisAlignedBB box, float yaw, float pitch, float maxAngle, float smoothFactor) {
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        return getRotationsTo(cx, cy, cz, yaw, pitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsToBox(AxisAlignedBB box, float yaw, float pitch, float maxAngle, float smoothFactor, int mode) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double cx, cy, cz;
        if (mode == 0) {
            cx = (box.minX + box.maxX) * 0.5;
            cy = (box.minY + box.maxY) * 0.5;
            cz = (box.minZ + box.maxZ) * 0.5;
        } else if (mode == 1) {
            cx = (box.minX + box.maxX) * 0.5;
            cy = box.minY + (box.maxY - box.minY) * 0.85;
            cz = (box.minZ + box.maxZ) * 0.5;
        } else if (mode == 2) {
            double range = (box.maxX - box.minX) * 0.3;
            cx = (box.minX + box.maxX) * 0.5 + (rand.nextDouble() - 0.5) * range;
            cy = box.minY + (box.maxY - box.minY) * (0.3 + rand.nextDouble() * 0.5);
            cz = (box.minZ + box.maxZ) * 0.5 + (rand.nextDouble() - 0.5) * range;
        } else {
            cx = (box.minX + box.maxX) * 0.5;
            cy = (box.minY + box.maxY) * 0.5;
            cz = (box.minZ + box.maxZ) * 0.5;
        }
        return getRotationsTo(cx, cy, cz, yaw, pitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float yaw, float pitch) {
        return getRotationsTo(targetX, targetY, targetZ, yaw, pitch, 180.0F, 0.0F);
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float yaw, float pitch, float maxAngle, float smoothFactor) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double dx = targetX - eye.xCoord;
        double dy = targetY - eye.yCoord;
        double dz = targetZ - eye.zCoord;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float rawYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float rawPitch = (float) (-Math.atan2(dy, horiz) * 180.0 / Math.PI);
        float diffYaw = normalizeAngle(rawYaw - yaw);
        float diffPitch = normalizeAngle(rawPitch - pitch);
        float maxYaw = Math.min(maxAngle, 180.0F);
        float maxPitch = Math.min(maxAngle, 180.0F);
        diffYaw = MathHelper.clamp_float(diffYaw, -maxYaw, maxYaw);
        diffPitch = MathHelper.clamp_float(diffPitch, -maxPitch, maxPitch);
        if (smoothFactor > 0.0F) {
            float factor = 1.0F - Math.max(0.0F, Math.min(1.0F, smoothFactor + (rand.nextFloat() - 0.5F) * 0.2F));
            diffYaw *= factor;
            diffPitch *= factor;
        }
        float newYaw = yaw + diffYaw;
        float newPitch = MathHelper.clamp_float(pitch + diffPitch, -90.0F, 90.0F);
        return new float[]{newYaw, newPitch};
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float yaw, float pitch, float maxAngle, float smoothFactor, float jitter) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double dx = targetX - eye.xCoord;
        double dy = targetY - eye.yCoord;
        double dz = targetZ - eye.zCoord;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float rawYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float rawPitch = (float) (-Math.atan2(dy, horiz) * 180.0 / Math.PI);
        float diffYaw = normalizeAngle(rawYaw - yaw);
        float diffPitch = normalizeAngle(rawPitch - pitch);
        float maxYaw = Math.min(maxAngle, 180.0F);
        float maxPitch = Math.min(maxAngle, 180.0F);
        diffYaw = MathHelper.clamp_float(diffYaw, -maxYaw, maxYaw);
        diffPitch = MathHelper.clamp_float(diffPitch, -maxPitch, maxPitch);
        if (smoothFactor > 0.0F) {
            float factor = 1.0F - Math.max(0.0F, Math.min(1.0F, smoothFactor + (rand.nextFloat() - 0.5F) * 0.2F));
            diffYaw *= factor;
            diffPitch *= factor;
        }
        float newYaw = yaw + diffYaw + (rand.nextFloat() - 0.5F) * jitter;
        float newPitch = MathHelper.clamp_float(pitch + diffPitch + (rand.nextFloat() - 0.5F) * jitter * 0.7F, -90.0F, 90.0F);
        return new float[]{newYaw, newPitch};
    }

    // ===== 兼容旧用法：直接使用相对偏移量（不减去眼睛位置） =====
    public static float[] getRotationsToRelative(double dx, double dy, double dz, float yaw, float pitch) {
        return getRotationsToRelative(dx, dy, dz, yaw, pitch, 180.0F, 0.0F);
    }

    public static float[] getRotationsToRelative(double dx, double dy, double dz, float yaw, float pitch, float maxAngle, float smoothFactor) {
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float rawYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float rawPitch = (float) (-Math.atan2(dy, horiz) * 180.0 / Math.PI);
        float diffYaw = normalizeAngle(rawYaw - yaw);
        float diffPitch = normalizeAngle(rawPitch - pitch);
        float maxYaw = Math.min(maxAngle, 180.0F);
        float maxPitch = Math.min(maxAngle, 180.0F);
        diffYaw = MathHelper.clamp_float(diffYaw, -maxYaw, maxYaw);
        diffPitch = MathHelper.clamp_float(diffPitch, -maxPitch, maxPitch);
        if (smoothFactor > 0.0F) {
            float factor = 1.0F - Math.max(0.0F, Math.min(1.0F, smoothFactor + (rand.nextFloat() - 0.5F) * 0.2F));
            diffYaw *= factor;
            diffPitch *= factor;
        }
        float newYaw = yaw + diffYaw;
        float newPitch = MathHelper.clamp_float(pitch + diffPitch, -90.0F, 90.0F);
        return new float[]{newYaw, newPitch};
    }

    public static float[] getSilentRotations(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float maxStep) {
        float dY = normalizeAngle(targetYaw - currentYaw);
        float dP = normalizeAngle(targetPitch - currentPitch);
        if (Math.abs(dY) > maxStep) dY = Math.signum(dY) * maxStep;
        if (Math.abs(dP) > maxStep) dP = Math.signum(dP) * maxStep;
        return new float[]{currentYaw + dY, MathHelper.clamp_float(currentPitch + dP, -90.0F, 90.0F)};
    }

    public static float[] getLegitRotations(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float speed, float accel, float damp) {
        float dY = normalizeAngle(targetYaw - currentYaw);
        float dP = normalizeAngle(targetPitch - currentPitch);
        if (Math.abs(dY) < 0.05F && Math.abs(dP) < 0.05F) {
            return new float[]{targetYaw, targetPitch};
        }
        if (Math.abs(dY) > speed) dY = Math.signum(dY) * speed;
        if (Math.abs(dP) > speed) dP = Math.signum(dP) * speed;
        if (Math.abs(dY) < 2.0F) dY *= 0.8F;
        if (Math.abs(dP) < 2.0F) dP *= 0.8F;
        float newYaw = currentYaw + dY;
        float newPitch = MathHelper.clamp_float(currentPitch + dP, -90.0F, 90.0F);
        return new float[]{newYaw, newPitch};
    }

    public static float angleToEntity(Entity entity) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double dx = entity.posX - eye.xCoord;
        double dz = entity.posZ - eye.zCoord;
        float yawTo = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        return Math.abs(normalizeAngle(yawTo - mc.thePlayer.rotationYaw));
    }

    public static float angleToEntity(Entity entity, float currentYaw) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double dx = entity.posX - eye.xCoord;
        double dz = entity.posZ - eye.zCoord;
        float yawTo = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        return Math.abs(normalizeAngle(yawTo - currentYaw));
    }

    public static boolean isInFOV(Entity entity, float fov) {
        return angleToEntity(entity) <= fov;
    }

    public static boolean isInFOV(Entity entity, float fov, float currentYaw) {
        return angleToEntity(entity, currentYaw) <= fov;
    }

    public static MovingObjectPosition rayTrace(float yaw, float pitch, double distance, float partialTicks) {
        Vec3 eye = mc.thePlayer.getPositionEyes(partialTicks);
        Vec3 look = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 target = eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return mc.theWorld.rayTraceBlocks(eye, target);
    }

    public static MovingObjectPosition rayTrace(Entity entity) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
        Vec3 boxPoint = clampPointToBox(eye, box);
        double dx = boxPoint.xCoord - eye.xCoord;
        double dy = boxPoint.yCoord - eye.yCoord;
        double dz = boxPoint.zCoord - eye.zCoord;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) return null;
        double extend = Math.max(6.0, len + 2.0);
        Vec3 farEnd = new Vec3(eye.xCoord + dx / len * extend, eye.yCoord + dy / len * extend, eye.zCoord + dz / len * extend);
        MovingObjectPosition boxIntercept = box.calculateIntercept(eye, farEnd);
        double boxDist = (boxIntercept == null) ? len : eye.distanceTo(boxIntercept.hitVec);
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eye, farEnd);
        if (blockHit == null) return null;
        double blockDist = eye.distanceTo(blockHit.hitVec);
        return (blockDist >= boxDist - 1.0E-4) ? null : blockHit;
    }

    public static MovingObjectPosition rayTrace(AxisAlignedBB box, float yaw, float pitch, double distance) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = ((IAccessorEntity) mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 target = eye.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
        return box.calculateIntercept(eye, target);
    }

    public static float quantizeAngle(float angle) {
        return quantizeAngle(angle, 2);
    }

    public static float quantizeAngle(float angle, int places) {
        double scale = Math.pow(10.0, places);
        return (float) (Math.round(angle * scale) / scale);
    }

    public static float quantizeAngleStep(float angle, float step) {
        return (float) (angle - angle % step);
    }

    public static float smoothAngle(float delta, float smoothFactor) {
        float factor = 0.5F + 0.5F * (1.0F - Math.max(0.0F, Math.min(1.0F, smoothFactor + rand.nextFloat() - 0.5F)));
        return delta * factor;
    }

    public static float wrapAngleDiff(float angle, float base) {
        return base + MathHelper.wrapAngleTo180_float(angle - base);
    }

    public static float clampAngle(float angle, float max) {
        return MathHelper.clamp_float(angle, -max, max);
    }

    public static float getYawBetween(double x1, double z1, double x2, double z2) {
        return (float) (Math.atan2(z2 - z1, x2 - x1) * 180.0 / Math.PI) - 90.0F;
    }

    public static double distanceToBox(Entity entity, Vec3 point) {
        float border = entity.getCollisionBorderSize();
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
        return distanceToBoxFromPoint(box, point);
    }

    public static Vec3 getAimPoint(EntityLivingBase entity, int mode) {
        AxisAlignedBB box = entity.getEntityBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        if (mode == 0) {
            return new Vec3(cx, cy, cz);
        } else if (mode == 1) {
            return new Vec3(cx, box.minY + (box.maxY - box.minY) * 0.85, cz);
        } else if (mode == 2) {
            double rangeX = (box.maxX - box.minX) * 0.25;
            double rangeY = (box.maxY - box.minY) * 0.25;
            double rangeZ = (box.maxZ - box.minZ) * 0.25;
            return new Vec3(cx + (rand.nextDouble() - 0.5) * rangeX,
                    box.minY + (box.maxY - box.minY) * (0.3 + rand.nextDouble() * 0.4),
                    cz + (rand.nextDouble() - 0.5) * rangeZ);
        } else {
            return new Vec3(cx, cy, cz);
        }
    }

    public static float[] getRotationsToEntity(Entity entity, float currentYaw, float currentPitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double dx = entity.posX - eye.xCoord;
        double dy = entity.posY + entity.getEyeHeight() - eye.yCoord;
        double dz = entity.posZ - eye.zCoord;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-Math.atan2(dy, horiz) * 180.0 / Math.PI);
        return new float[]{normalizeAngle(yaw), MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    public static float[] getRotationsToEntity(Entity entity, float currentYaw, float currentPitch, float maxAngle) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double dx = entity.posX - eye.xCoord;
        double dy = entity.posY + entity.getEyeHeight() - eye.yCoord;
        double dz = entity.posZ - eye.zCoord;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-Math.atan2(dy, horiz) * 180.0 / Math.PI);
        float dY = normalizeAngle(yaw - currentYaw);
        float dP = normalizeAngle(pitch - currentPitch);
        if (Math.abs(dY) > maxAngle) dY = Math.signum(dY) * maxAngle;
        if (Math.abs(dP) > maxAngle) dP = Math.signum(dP) * maxAngle;
        return new float[]{currentYaw + dY, MathHelper.clamp_float(currentPitch + dP, -90.0F, 90.0F)};
    }

    public static float[] smoothRotations(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float smoothFactor) {
        float dY = normalizeAngle(targetYaw - currentYaw);
        float dP = normalizeAngle(targetPitch - currentPitch);
        dY *= 1.0F - smoothFactor;
        dP *= 1.0F - smoothFactor;
        if (Math.abs(dY) < 0.01F) dY = 0.0F;
        if (Math.abs(dP) < 0.01F) dP = 0.0F;
        return new float[]{currentYaw + dY, MathHelper.clamp_float(currentPitch + dP, -90.0F, 90.0F)};
    }

    public static float[] smoothRotationsWithJitter(float currentYaw, float currentPitch, float targetYaw, float targetPitch, float smoothFactor, float jitter) {
        float dY = normalizeAngle(targetYaw - currentYaw);
        float dP = normalizeAngle(targetPitch - currentPitch);
        dY *= 1.0F - smoothFactor;
        dP *= 1.0F - smoothFactor;
        if (Math.abs(dY) < 0.01F) dY = 0.0F;
        if (Math.abs(dP) < 0.01F) dP = 0.0F;
        float newYaw = currentYaw + dY + (rand.nextFloat() - 0.5F) * jitter;
        float newPitch = MathHelper.clamp_float(currentPitch + dP + (rand.nextFloat() - 0.5F) * jitter * 0.5F, -90.0F, 90.0F);
        return new float[]{newYaw, newPitch};
    }

    public static float[] getRotationsToPredicted(Entity target, float currentYaw, float currentPitch, int ticks) {
        double x = target.posX + target.motionX * ticks;
        double y = target.posY + target.motionY * ticks + target.getEyeHeight();
        double z = target.posZ + target.motionZ * ticks;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        double dx = x - eye.xCoord;
        double dy = y - eye.yCoord;
        double dz = z - eye.zCoord;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-Math.atan2(dy, horiz) * 180.0 / Math.PI);
        return new float[]{normalizeAngle(yaw), MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    public static final class RotationVec {
        public float x;
        public float y;

        public RotationVec(RotationVec vec) {
            this(vec.x, vec.y);
        }

        public RotationVec(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public RotationVec add(float x, float y) {
            return new RotationVec(this.x + x, this.y + y);
        }

        public float getX() {
            return this.x;
        }

        public float getY() {
            return this.y;
        }

        public void setX(float x) {
            this.x = x;
        }

        public void setY(float y) {
            this.y = y;
        }
    }
}