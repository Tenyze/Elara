package elara.mixin;

import elara.Elara;
import elara.event.EventManager;
import elara.events.HitSlowDownEvent;
import elara.module.combat.Knockback;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityPlayer.class}, priority = 9999)
public abstract class MixinEntityPlayer extends MixinEntityLivingBase {

    @Unique
    private HitSlowDownEvent hitSlowDownEvent;

    @Inject(
            method = {"attackTargetEntityWithCurrentItem"},
            at = {@At("HEAD")}
    )
    private void onAttackTargetEntityWithCurrentItemHead(CallbackInfo callbackInfo) {
        hitSlowDownEvent = new HitSlowDownEvent();
        EventManager.call(hitSlowDownEvent);
    }

    @ModifyConstant(
            method = {"attackTargetEntityWithCurrentItem"},
            constant = {@Constant(
                    doubleValue = 0.6
            )}
    )
    private double attackTargetEntityWithCurrentItem(double speed) {
        if (Elara.moduleManager == null) {
            return speed;
        }
        if (Knockback.blinkActive) {
            return 1.0;
        }
        if (hitSlowDownEvent == null) {
            return speed;
        }
        return hitSlowDownEvent.getSlowDown();
    }

    @Redirect(
            method = {"attackTargetEntityWithCurrentItem"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"
            )
    )
    private void setSprinnt(EntityPlayer entityPlayer, boolean boolean2) {
        if (Elara.moduleManager != null) {
            if (Knockback.blinkActive) {
                return;
            }
            if (hitSlowDownEvent != null && hitSlowDownEvent.isSprint()) {
                return;
            }
            entityPlayer.setSprinting(boolean2);
        }
    }
}
