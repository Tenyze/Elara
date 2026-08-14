package elara.management;

import elara.event.EventTarget;
import elara.event.types.EventType;
import elara.event.types.Priority;
import elara.events.Render3DEvent;
import elara.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class RotationManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private float targetYaw;
    private float targetPitch;
    private float currentYaw;
    private float currentPitch;
    private int priority;
    private boolean rotated;
    private float rotationSpeed = 1.0F;
    private boolean hasTarget;

    public RotationManager() {
        this.targetYaw = Float.NaN;
        this.targetPitch = Float.NaN;
        this.currentYaw = Float.NaN;
        this.currentPitch = Float.NaN;
        this.priority = Integer.MIN_VALUE;
        this.rotated = false;
        this.hasTarget = false;
    }

    private void applyRotation(float partialTicks) {
        if (mc.thePlayer == null || !this.hasTarget) {
            return;
        }
        
        float interpolatedYaw = interpolateRotation(mc.thePlayer.rotationYaw, this.targetYaw, this.rotationSpeed);
        float interpolatedPitch = interpolateRotation(mc.thePlayer.rotationPitch, this.targetPitch, this.rotationSpeed);
        
        mc.thePlayer.prevRotationYaw = mc.thePlayer.rotationYaw;
        mc.thePlayer.rotationYaw = interpolatedYaw;
        
        mc.thePlayer.prevRotationPitch = mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationPitch = MathHelper.clamp_float(interpolatedPitch, -90.0F, 90.0F);
        
        float yawDiff = MathHelper.wrapAngleTo180_float(this.targetYaw - mc.thePlayer.rotationYaw);
        float pitchDiff = this.targetPitch - mc.thePlayer.rotationPitch;
        
        if (Math.abs(yawDiff) < 0.5F && Math.abs(pitchDiff) < 0.5F) {
            mc.thePlayer.rotationYaw = this.targetYaw;
            mc.thePlayer.rotationPitch = this.targetPitch;
            this.hasTarget = false;
        }
    }

    private float interpolateRotation(float current, float target, float speed) {
        float diff = MathHelper.wrapAngleTo180_float(target - current);
        float step = diff * speed;
        return current + step;
    }

    private void resetRotationState() {
        this.targetYaw = Float.NaN;
        this.targetPitch = Float.NaN;
        this.currentYaw = Float.NaN;
        this.currentPitch = Float.NaN;
        this.priority = Integer.MIN_VALUE;
        this.rotated = false;
        this.hasTarget = false;
    }

    public void setRotation(float yaw, float pitch, int priority, boolean force) {
        if (this.priority <= priority || force) {
            this.priority = priority;
            this.targetYaw = yaw;
            this.targetPitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);
            this.rotated = force;
            this.hasTarget = true;
            this.applyRotation(0.0F);
        }
    }

    public void setRotationSpeed(float speed) {
        this.rotationSpeed = MathHelper.clamp_float(speed, 0.1F, 1.0F);
    }

    public float getRotationSpeed() {
        return this.rotationSpeed;
    }

    public boolean isRotated() {
        return this.rotated;
    }

    public boolean hasTarget() {
        return this.hasTarget;
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        this.applyRotation(1.0F);
        if (!this.hasTarget) {
            this.resetRotationState();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onRender3D(Render3DEvent event) {
        this.applyRotation(event.getPartialTicks());
    }
}
