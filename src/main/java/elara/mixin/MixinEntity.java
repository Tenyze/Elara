package elara.mixin;

import elara.Elara;
import elara.event.EventManager;
import elara.events.KnockbackEvent;
import elara.events.SafeWalkEvent;
import elara.module.render.FreeLook;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {Entity.class}, priority = 9999)
public abstract class MixinEntity {
    @Shadow
    public World worldObj;
    @Shadow
    public double posX;
    @Shadow
    public double posY;
    @Shadow
    public double posZ;
    @Shadow
    public double motionX;
    @Shadow
    public double motionY;
    @Shadow
    public double motionZ;
    @Shadow
    public float rotationYaw;
    @Shadow
    public float rotationPitch;
    @Shadow
    public float prevRotationYaw;
    @Shadow
    public float prevRotationPitch;
    @Shadow
    public boolean onGround;

    @Shadow
    public boolean isRiding() {
        return false;
    }

    @Inject(
            method = {"setVelocity"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void setVelocity(double double1, double double2, double double3, CallbackInfo callbackInfo) {
        if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
            KnockbackEvent event = new KnockbackEvent(double1, double2, double3);
            EventManager.call(event);
            if (event.isCancelled()) {
                callbackInfo.cancel();
                this.motionX = event.getX();
                this.motionY = event.getY();
                this.motionZ = event.getZ();
            }
        }
    }

    @Inject(
            method = {"setAngles"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void onSetAngles(float yaw, float pitch, CallbackInfo callbackInfo) {
        if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
            // FreeLook 激活时，鼠标输入路由到 cameraYaw/Pitch，不改变玩家实际朝向
            FreeLook freeLook = FreeLook.INSTANCE;
            if (freeLook != null && freeLook.isActive()) {
                freeLook.cameraYaw += yaw * 0.15F;
                freeLook.cameraPitch -= pitch * 0.15F;
                freeLook.cameraPitch = MathHelper.clamp_float(freeLook.cameraPitch, -90.0F, 90.0F);
                callbackInfo.cancel();
                return;
            }
            if (Elara.rotationManager != null && Elara.rotationManager.isRotated()) {
                callbackInfo.cancel();
            }
        }
    }

    @ModifyVariable(
            method = {"moveEntity"},
            ordinal = 0,
            at = @At("STORE"),
            name = {"flag"}
    )
    private boolean moveEntity(boolean boolean1) {
        if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
            SafeWalkEvent event = new SafeWalkEvent(boolean1);
            EventManager.call(event);
            return event.isSafeWalk();
        } else {
            return boolean1;
        }
    }
}
